package com.hms.lab;

import com.hms.audit.AuditService;
import com.hms.entity.Hospital;
import com.hms.entity.LabOrder;
import com.hms.entity.LabOrderItem;
import com.hms.entity.LabTest;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.lab.dto.LabResultRequest;
import com.hms.repository.LabOrderItemRepository;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.LabResultRepository;
import com.hms.repository.PrescriptionRepository;
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
import java.util.Optional;

import static com.hms.testsupport.EntityTestSupport.mockTenantScopedFind;
import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabServiceTests {

    private final LabOrderRepository labOrderRepository = mock(LabOrderRepository.class);
    private final LabOrderItemRepository labOrderItemRepository = mock(LabOrderItemRepository.class);
    private final LabResultRepository labResultRepository = mock(LabResultRepository.class);
    private final PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AuditService auditService = mock(AuditService.class);

    private final LabService service = new LabService(
            labOrderRepository, labOrderItemRepository,
            labResultRepository, prescriptionRepository, entityManager, auditService
    );

    private User labUser;

    @BeforeEach
    void setUpTenantContextAndCurrentUser() throws Exception {
        TenantContext.set(1L);
        var constructor = Hospital.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Hospital hospital = constructor.newInstance();
        setField(hospital, Hospital.class, "id", 1L);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospital);

        labUser = newUser(9L, "lab1", User.Role.LAB);
        var principal = new HmsUserPrincipal(labUser);
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

    private Visit visitWithStatus(Visit.Status status) {
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        Visit visit = withId(new Visit(null, patient, null, null, Visit.VisitType.OPD, Visit.Status.WAITING_DOCTOR, ""), 20L);
        setField(visit, Visit.class, "status", status);
        return visit;
    }

    private static LabTest newLabTest(Long id, String name) throws Exception {
        var constructor = LabTest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LabTest test = constructor.newInstance();
        setField(test, LabTest.class, "name", name);
        return withId(test, id);
    }

    private static LabOrderItem newLabOrderItem(Long id, LabOrder order, LabTest test) {
        return withId(new LabOrderItem(order, test), id);
    }

    @Test
    void rejectsAVisitThatIsNotAwaitingLabWork() {
        Visit visit = visitWithStatus(Visit.Status.WAITING_DOCTOR);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        assertThatThrownBy(() -> service.recordLabResult(20L, 1L, new LabResultRequest("val", "", ""), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not awaiting lab work");
    }

    @Test
    void recordingTheSameTestTwiceIsIdempotent() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_LAB);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        LabTest test = newLabTest(7L, "CBC");
        LabOrder order = withId(new LabOrder(visit, visit.getPatient(), null), 30L);
        when(labOrderRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(order));

        LabOrderItem item = newLabOrderItem(40L, order, test);
        when(labOrderItemRepository.findById(40L)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderAndTest(order, test)).thenReturn(true);

        var response = service.recordLabResult(20L, 40L, new LabResultRequest("5.0", "4-6", ""), "127.0.0.1");

        assertThat(response.alreadyRecorded()).isTrue();
        assertThat(response.testName()).isEqualTo("CBC");
    }

    @Test
    void firstResultAdvancesLabOrderFromPendingToProcessing() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_LAB);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        LabTest cbc = newLabTest(7L, "CBC");
        LabTest xray = newLabTest(8L, "X-Ray");
        LabOrder order = withId(new LabOrder(visit, visit.getPatient(), null), 30L);
        when(labOrderRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(order));

        LabOrderItem item = newLabOrderItem(40L, order, cbc);
        when(labOrderItemRepository.findById(40L)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderAndTest(order, cbc)).thenReturn(false);
        // Two tests ordered, only one resulted so far -> not fully resulted yet.
        when(labOrderItemRepository.countByLabOrder(order)).thenReturn(2L);
        when(labResultRepository.countByLabOrder(order)).thenReturn(1L);

        var response = service.recordLabResult(20L, 40L, new LabResultRequest("5.0", "4-6", ""), "127.0.0.1");

        assertThat(response.alreadyRecorded()).isFalse();
        assertThat(order.getStatus()).isEqualTo(LabOrder.Status.PROCESSING);
        assertThat(visit.getStatus()).isEqualTo(Visit.Status.WAITING_LAB); // unchanged, not fully resulted
        verify(auditService).record(labUser, "RECORD_LAB_RESULT", "hospital_labresult", null, "127.0.0.1");
    }

    @Test
    void recordingTheSameTestTwiceDoesNotWriteASecondAuditLogEntry() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_LAB);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        LabTest test = newLabTest(7L, "CBC");
        LabOrder order = withId(new LabOrder(visit, visit.getPatient(), null), 30L);
        when(labOrderRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(order));

        LabOrderItem item = newLabOrderItem(40L, order, test);
        when(labOrderItemRepository.findById(40L)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderAndTest(order, test)).thenReturn(true);

        service.recordLabResult(20L, 40L, new LabResultRequest("5.0", "4-6", ""), "127.0.0.1");

        verify(auditService, org.mockito.Mockito.never()).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void lastResultRoutesVisitToWaitingPharmacyWhenAPrescriptionExists() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_LAB);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        LabTest cbc = newLabTest(7L, "CBC");
        LabOrder order = withId(new LabOrder(visit, visit.getPatient(), null), 30L);
        when(labOrderRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(order));

        LabOrderItem item = newLabOrderItem(40L, order, cbc);
        when(labOrderItemRepository.findById(40L)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderAndTest(order, cbc)).thenReturn(false);
        when(labOrderItemRepository.countByLabOrder(order)).thenReturn(1L);
        when(labResultRepository.countByLabOrder(order)).thenReturn(1L);
        when(prescriptionRepository.existsByVisit(visit)).thenReturn(true);

        var response = service.recordLabResult(20L, 40L, new LabResultRequest("5.0", "4-6", ""), "127.0.0.1");

        assertThat(order.getStatus()).isEqualTo(LabOrder.Status.COMPLETED);
        assertThat(visit.getStatus()).isEqualTo(Visit.Status.WAITING_PHARMACY);
        assertThat(response.visitStatus()).isEqualTo("WAITING_PHARMACY");
    }

    @Test
    void lastResultCompletesTheVisitWhenNoPrescriptionExists() throws Exception {
        Visit visit = visitWithStatus(Visit.Status.WAITING_LAB);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        LabTest cbc = newLabTest(7L, "CBC");
        LabOrder order = withId(new LabOrder(visit, visit.getPatient(), null), 30L);
        when(labOrderRepository.findByVisitForUpdate(visit)).thenReturn(Optional.of(order));

        LabOrderItem item = newLabOrderItem(40L, order, cbc);
        when(labOrderItemRepository.findById(40L)).thenReturn(Optional.of(item));
        when(labResultRepository.existsByLabOrderAndTest(order, cbc)).thenReturn(false);
        when(labOrderItemRepository.countByLabOrder(order)).thenReturn(1L);
        when(labResultRepository.countByLabOrder(order)).thenReturn(1L);
        when(prescriptionRepository.existsByVisit(visit)).thenReturn(false);

        var response = service.recordLabResult(20L, 40L, new LabResultRequest("5.0", "4-6", ""), "127.0.0.1");

        assertThat(visit.getStatus()).isEqualTo(Visit.Status.COMPLETED);
        assertThat(response.visitStatus()).isEqualTo("COMPLETED");
    }
}
