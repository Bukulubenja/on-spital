package com.hms.doctor;

import com.hms.entity.Hospital;
import com.hms.entity.LabOrder;
import com.hms.entity.LabTest;
import com.hms.entity.Patient;
import com.hms.entity.QueueTicket;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.repository.DrugRepository;
import com.hms.repository.LabOrderItemRepository;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.LabTestRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.PrescriptionItemRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.QueueTicketRepository;
import com.hms.repository.VisitRepository;
import com.hms.repository.VitalSignsRepository;
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

import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorServiceTests {

    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final QueueTicketRepository queueTicketRepository = mock(QueueTicketRepository.class);
    private final VitalSignsRepository vitalSignsRepository = mock(VitalSignsRepository.class);
    private final MedicalRecordRepository medicalRecordRepository = mock(MedicalRecordRepository.class);
    private final PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);
    private final PrescriptionItemRepository prescriptionItemRepository = mock(PrescriptionItemRepository.class);
    private final LabOrderRepository labOrderRepository = mock(LabOrderRepository.class);
    private final LabOrderItemRepository labOrderItemRepository = mock(LabOrderItemRepository.class);
    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final LabTestRepository labTestRepository = mock(LabTestRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final DoctorService service = new DoctorService(
            visitRepository, queueTicketRepository, vitalSignsRepository, medicalRecordRepository,
            prescriptionRepository, prescriptionItemRepository, labOrderRepository, labOrderItemRepository,
            drugRepository, labTestRepository, entityManager
    );

    private User doctorUser;

    @BeforeEach
    void setUpTenantContextAndCurrentDoctor() throws Exception {
        TenantContext.set(1L);
        var hospitalConstructor = Hospital.class.getDeclaredConstructor();
        hospitalConstructor.setAccessible(true);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospitalConstructor.newInstance());

        doctorUser = newUser(5L, "drjane", User.Role.DOCTOR);
        var principal = new HmsUserPrincipal(doctorUser);
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

    private Visit waitingVisit(User doctor) {
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        return withId(new Visit(null, patient, doctor, null, Visit.VisitType.OPD, Visit.Status.WAITING_DOCTOR, ""), 20L);
    }

    @Test
    void startConsultationRejectsAVisitNotWaitingForADoctor() {
        Visit visit = waitingVisit(doctorUser);
        setField(visit, Visit.class, "status", Visit.Status.IN_CONSULTATION);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service.startConsultation(20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be started");
    }

    @Test
    void startConsultationRejectsAVisitAssignedToAnotherDoctor() throws Exception {
        User anotherDoctor = newUser(99L, "drsmith", User.Role.DOCTOR);
        Visit visit = waitingVisit(anotherDoctor);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service.startConsultation(20L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void startConsultationMarksTheQueueTicketServed() {
        Visit visit = waitingVisit(doctorUser);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        QueueTicket ticket = new QueueTicket(visit, 3);
        when(queueTicketRepository.findByVisit(visit)).thenReturn(Optional.of(ticket));

        var response = service.startConsultation(20L);

        assertThat(response.status()).isEqualTo("IN_CONSULTATION");
        assertThat(ticket.isServed()).isTrue();
    }

    @Test
    void clinicalActionsRejectAVisitThatIsNotInAnActiveConsultation() {
        Visit visit = waitingVisit(doctorUser); // still WAITING_DOCTOR, not IN_CONSULTATION
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service.recordDiagnosis(20L, new com.hms.doctor.dto.DiagnosisRequest("Flu", "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active consultation");
    }

    @Test
    void addLabTestIsIdempotentWhenTheSameTestIsAlreadyOrdered() throws Exception {
        Visit visit = waitingVisit(doctorUser);
        setField(visit, Visit.class, "status", Visit.Status.IN_CONSULTATION);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));

        LabTest test = newLabTest(7L, "CBC");
        when(labTestRepository.findById(7L)).thenReturn(Optional.of(test));

        LabOrder existingOrder = new LabOrder(visit, visit.getPatient(), doctorUser);
        when(labOrderRepository.findByVisit(visit)).thenReturn(Optional.of(existingOrder));
        when(labOrderItemRepository.existsByLabOrderAndTest(existingOrder, test)).thenReturn(true);

        var response = service.addLabTest(20L, 7L);

        assertThat(response.alreadyOrdered()).isTrue();
        assertThat(response.testName()).isEqualTo("CBC");
        verify(labOrderItemRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeVisitRoutesToWaitingLabWhenATestWasOrdered() {
        Visit visit = waitingVisit(doctorUser);
        setField(visit, Visit.class, "status", Visit.Status.IN_CONSULTATION);
        when(visitRepository.findById(20L)).thenReturn(Optional.of(visit));
        when(labOrderRepository.existsByVisit(visit)).thenReturn(true);
        when(prescriptionRepository.existsByVisit(visit)).thenReturn(true);

        var response = service.completeVisit(20L);

        assertThat(response.status()).isEqualTo("WAITING_LAB");
    }

    private static LabTest newLabTest(Long id, String name) throws Exception {
        var constructor = LabTest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LabTest test = constructor.newInstance();
        setField(test, LabTest.class, "name", name);
        return withId(test, id);
    }
}
