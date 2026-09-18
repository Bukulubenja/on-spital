package com.hms.reception;

import com.hms.entity.Appointment;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.QueueTicket;
import com.hms.reception.dto.PatientRequest;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DepartmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.QueueTicketRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.VisitRepository;
import com.hms.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static com.hms.testsupport.EntityTestSupport.mockTenantScopedFind;
import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceptionServiceTests {

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final QueueTicketRepository queueTicketRepository = mock(QueueTicketRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final ReceptionService service = new ReceptionService(
            patientRepository, appointmentRepository, visitRepository,
            queueTicketRepository, userRepository, departmentRepository, entityManager
    );

    @BeforeEach
    void setUpTenantContext() throws Exception {
        TenantContext.set(1L);
        var constructor = Hospital.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Hospital hospital = constructor.newInstance();
        setField(hospital, Hospital.class, "id", 1L);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospital);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private Appointment scheduledAppointment() {
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        return withId(new Appointment(patient, null, null, OffsetDateTime.now().plusDays(1), "Checkup", null, null), 10L);
    }

    @Test
    void checkInRejectsAppointmentThatIsNotScheduled() {
        Appointment appointment = scheduledAppointment();
        setField(appointment, Appointment.class, "status", Appointment.Status.CANCELLED);
        mockTenantScopedFind(entityManager, Appointment.class, 10L, appointment);

        assertThatThrownBy(() -> service.checkIn(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only scheduled appointments");
    }

    @Test
    void checkInRejectsAnAppointmentAlreadyCheckedIn() {
        Appointment appointment = scheduledAppointment();
        mockTenantScopedFind(entityManager, Appointment.class, 10L, appointment);
        when(visitRepository.existsByAppointment(appointment)).thenReturn(true);

        assertThatThrownBy(() -> service.checkIn(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been checked in");
    }

    @Test
    void checkInStartsQueueAtOneWhenNoTicketsExistToday() {
        Appointment appointment = scheduledAppointment();
        mockTenantScopedFind(entityManager, Appointment.class, 10L, appointment);
        when(visitRepository.existsByAppointment(appointment)).thenReturn(false);
        when(queueTicketRepository.findFirstByCreatedAtBetweenOrderByQueueNumberDesc(any(), any()))
                .thenReturn(Optional.empty());

        var response = service.checkIn(10L);

        assertThat(response.queueNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("WAITING_DOCTOR");
    }

    @Test
    void checkInContinuesTheQueueFromTheDaysLastTicket() {
        Appointment appointment = scheduledAppointment();
        mockTenantScopedFind(entityManager, Appointment.class, 10L, appointment);
        when(visitRepository.existsByAppointment(appointment)).thenReturn(false);

        QueueTicket lastTicket = new QueueTicket(null, 5);
        when(queueTicketRepository.findFirstByCreatedAtBetweenOrderByQueueNumberDesc(any(), any()))
                .thenReturn(Optional.of(lastTicket));

        var response = service.checkIn(10L);

        assertThat(response.queueNumber()).isEqualTo(6);
    }

    @Test
    void registerPatientFormatsPatientNumberFromTheGeneratedId() {
        doAnswer(invocation -> {
            Patient patient = invocation.getArgument(0);
            withId(patient, 42L);
            return patient;
        }).when(patientRepository).save(any(Patient.class));

        PatientRequest request = new PatientRequest(
                "John Smith", Patient.Gender.MALE, LocalDate.of(1985, 5, 20),
                "555-0199", "", "", "", ""
        );

        Patient saved = service.registerPatient(request);

        assertThat(saved.getPatientNumber()).isEqualTo("P-000042");
    }
}
