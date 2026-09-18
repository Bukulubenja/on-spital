package com.hms.pharmacy;

import com.hms.audit.AuditService;
import com.hms.entity.Drug;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.Prescription;
import com.hms.entity.PrescriptionItem;
import com.hms.entity.Stock;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.repository.PrescriptionItemRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.StockRepository;
import com.hms.repository.StockTransactionRepository;
import com.hms.security.HmsUserPrincipal;
import com.hms.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hms.testsupport.EntityTestSupport.mockTenantScopedFind;
import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PharmacyServiceTests {

    private final PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);
    private final PrescriptionItemRepository prescriptionItemRepository = mock(PrescriptionItemRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final StockTransactionRepository stockTransactionRepository = mock(StockTransactionRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AuditService auditService = mock(AuditService.class);

    private final PharmacyService service = new PharmacyService(
            prescriptionRepository, prescriptionItemRepository,
            stockRepository, stockTransactionRepository, entityManager, auditService
    );

    private User pharmacistUser;

    @BeforeEach
    void setUpTenantContextAndCurrentPharmacist() throws Exception {
        TenantContext.set(1L);
        var hospitalConstructor = Hospital.class.getDeclaredConstructor();
        hospitalConstructor.setAccessible(true);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospitalConstructor.newInstance());

        pharmacistUser = newUser(9L, "pharm1", User.Role.PHARMACIST);
        var principal = new HmsUserPrincipal(pharmacistUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static User newUser(Long id, String username, User.Role role) throws Exception {
        var constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        User user = constructor.newInstance();
        setField(user, User.class, "username", username);
        setField(user, User.class, "password", "irrelevant");
        setField(user, User.class, "role", role);
        setField(user, User.class, "active", true);
        return withId(user, id);
    }

    private static Drug newDrug(Long id, String name) throws Exception {
        var constructor = Drug.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Drug drug = constructor.newInstance();
        setField(drug, Drug.class, "name", name);
        return withId(drug, id);
    }

    private Visit visitWithStatus(Visit.Status status) {
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        Visit visit = withId(new Visit(null, patient, null, null, Visit.VisitType.OPD, status, ""), 20L);
        return visit;
    }

    private static Prescription newPrescription(Long id, Visit visit) {
        return withId(new Prescription(visit, null, visit.getPatient()), id);
    }

    private static PrescriptionItem newItem(Long id, Prescription prescription, Drug drug, int quantity) {
        return withId(new PrescriptionItem(prescription, drug, quantity, "1 tab", "twice daily", "5 days", ""), id);
    }

    @Test
    void dispenseRejectsAVisitThatIsNotAwaitingPharmacy() {
        Visit visit = visitWithStatus(Visit.Status.WAITING_LAB);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        assertThatThrownBy(() -> service.dispensePrescriptionItem(20L, 1L, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not awaiting pharmacy");
    }

    @Test
    void dispensingAnAlreadyDispensedItemIsIdempotentAndTouchesNoStock() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_PHARMACY);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        Drug paracetamol = newDrug(3L, "Paracetamol");
        Prescription prescription = newPrescription(30L, visit);
        PrescriptionItem item = newItem(40L, prescription, paracetamol, 10);
        item.markDispensed(newUser(9L, "pharm1", User.Role.PHARMACIST));
        when(prescriptionItemRepository.findById(40L)).thenReturn(Optional.of(item));

        var response = service.dispensePrescriptionItem(20L, 40L, "127.0.0.1");

        assertThat(response.alreadyDispensed()).isTrue();
        assertThat(response.drugName()).isEqualTo("Paracetamol");
        verify(stockRepository, org.mockito.Mockito.never())
                .findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(any(), anyInt());
        verify(auditService, org.mockito.Mockito.never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void deductsFromEarliestExpiryBatchesFirstAndMarksItemDispensed() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_PHARMACY);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        Drug paracetamol = newDrug(3L, "Paracetamol");
        Prescription prescription = newPrescription(30L, visit);
        PrescriptionItem item = newItem(40L, prescription, paracetamol, 12);
        when(prescriptionItemRepository.findById(40L)).thenReturn(Optional.of(item));

        Stock earlyBatch = new Stock(paracetamol, 10, LocalDate.of(2026, 1, 1), "BATCH-EARLY");
        Stock laterBatch = new Stock(paracetamol, 10, LocalDate.of(2027, 1, 1), "BATCH-LATER");
        when(stockRepository.findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(paracetamol, 0))
                .thenReturn(List.of(earlyBatch, laterBatch));
        when(prescriptionItemRepository.existsByPrescriptionAndDispensedFalse(prescription)).thenReturn(false);

        var response = service.dispensePrescriptionItem(20L, 40L, "127.0.0.1");

        assertThat(response.alreadyDispensed()).isFalse();
        assertThat(earlyBatch.getQuantity()).isZero();
        assertThat(laterBatch.getQuantity()).isEqualTo(8);
        assertThat(item.isDispensed()).isTrue();
        verify(stockTransactionRepository).save(any());
        verify(auditService).record(pharmacistUser, "DISPENSE_PRESCRIPTION_ITEM", "hospital_prescriptionitem", 40L, "127.0.0.1");
    }

    @Test
    void insufficientStockAcrossAllBatchesLeavesEverythingUnchanged() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_PHARMACY);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        Drug paracetamol = newDrug(3L, "Paracetamol");
        Prescription prescription = newPrescription(30L, visit);
        PrescriptionItem item = newItem(40L, prescription, paracetamol, 50);
        when(prescriptionItemRepository.findById(40L)).thenReturn(Optional.of(item));

        Stock onlyBatch = new Stock(paracetamol, 5, LocalDate.of(2026, 1, 1), "BATCH-SMALL");
        when(stockRepository.findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(paracetamol, 0))
                .thenReturn(List.of(onlyBatch));

        assertThatThrownBy(() -> service.dispensePrescriptionItem(20L, 40L, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not enough stock");

        assertThat(onlyBatch.getQuantity()).isEqualTo(5);
        assertThat(item.isDispensed()).isFalse();
        verify(stockTransactionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void lastRemainingItemDispensedCompletesTheVisit() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_PHARMACY);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        Drug paracetamol = newDrug(3L, "Paracetamol");
        Prescription prescription = newPrescription(30L, visit);
        PrescriptionItem item = newItem(40L, prescription, paracetamol, 5);
        when(prescriptionItemRepository.findById(40L)).thenReturn(Optional.of(item));

        Stock batch = new Stock(paracetamol, 20, LocalDate.of(2026, 1, 1), "BATCH-A");
        when(stockRepository.findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(paracetamol, 0))
                .thenReturn(List.of(batch));
        when(prescriptionItemRepository.existsByPrescriptionAndDispensedFalse(prescription)).thenReturn(false);

        var response = service.dispensePrescriptionItem(20L, 40L, "127.0.0.1");

        assertThat(visit.getStatus()).isEqualTo(Visit.Status.COMPLETED);
        assertThat(response.visitStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void moreItemsStillPendingKeepsVisitWaitingPharmacy() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_PHARMACY);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        Drug paracetamol = newDrug(3L, "Paracetamol");
        Prescription prescription = newPrescription(30L, visit);
        PrescriptionItem item = newItem(40L, prescription, paracetamol, 5);
        when(prescriptionItemRepository.findById(40L)).thenReturn(Optional.of(item));

        Stock batch = new Stock(paracetamol, 20, LocalDate.of(2026, 1, 1), "BATCH-A");
        when(stockRepository.findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(paracetamol, 0))
                .thenReturn(List.of(batch));
        when(prescriptionItemRepository.existsByPrescriptionAndDispensedFalse(prescription)).thenReturn(true);

        var response = service.dispensePrescriptionItem(20L, 40L, "127.0.0.1");

        assertThat(visit.getStatus()).isEqualTo(Visit.Status.WAITING_PHARMACY);
        assertThat(response.visitStatus()).isEqualTo("WAITING_PHARMACY");
    }
}
