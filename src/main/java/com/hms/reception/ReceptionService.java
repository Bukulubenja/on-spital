package com.hms.reception;

import com.hms.entity.Appointment;
import com.hms.entity.Department;
import com.hms.entity.Patient;
import com.hms.entity.QueueTicket;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.reception.dto.AppointmentRequest;
import com.hms.reception.dto.CheckInResponse;
import com.hms.reception.dto.PatientRequest;
import com.hms.reception.dto.QueueTicketResponse;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DepartmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.QueueTicketRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.VisitRepository;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors hospital/views.py's patient_create/appointment_create/
 * appointment_checkin plus the queue half of reception_dashboard — see the
 * Phase 2 plan for the field-by-field / rule-by-rule mapping.
 */
@Service
public class ReceptionService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final VisitRepository visitRepository;
    private final QueueTicketRepository queueTicketRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public ReceptionService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            VisitRepository visitRepository,
            QueueTicketRepository queueTicketRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.visitRepository = visitRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Patient registerPatient(PatientRequest request) {
        Patient patient = new Patient(
                request.fullName(), request.gender(), request.dateOfBirth(), request.phone(),
                request.address(), request.bloodGroup(), request.emergencyContactName(), request.emergencyContactPhone()
        );
        patient.setHospital(TenantScoping.currentHospitalReference(entityManager));

        // Temporary unique placeholder to avoid Django's own empty-string
        // collision window (see Phase 2 plan) — replaced below once we
        // have a real id to format P-{id:06d} from.
        patient.setPatientNumber("TMP-" + UUID.randomUUID());
        patientRepository.save(patient);
        patientRepository.flush();

        patient.setPatientNumber(String.format("P-%06d", patient.getId()));
        return patient;
    }

    @Transactional
    public Appointment bookAppointment(AppointmentRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        User doctor = userRepository.findById(request.doctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        if (doctor.getRole() != User.Role.DOCTOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected user is not a doctor");
        }
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        Appointment appointment = new Appointment(
                patient, doctor, department, request.appointmentDate(),
                request.reason(), request.consultationType(), request.meetingLink()
        );
        appointment.setHospital(TenantScoping.currentHospitalReference(entityManager));
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public CheckInResponse checkIn(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (appointment.getStatus() != Appointment.Status.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only scheduled appointments can be checked in");
        }
        if (visitRepository.existsByAppointment(appointment)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This appointment has already been checked in");
        }

        OffsetDateTime dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);

        // Locks the day's highest queue_number row for the rest of this
        // transaction — see QueueTicketRepository's Javadoc for the exact
        // Django analogue.
        int nextNumber = queueTicketRepository
                .findFirstByCreatedAtBetweenOrderByQueueNumberDesc(dayStart, dayEnd)
                .map(ticket -> ticket.getQueueNumber() + 1)
                .orElse(1);

        Visit visit = new Visit(
                appointment, appointment.getPatient(), appointment.getDoctor(), appointment.getDepartment(),
                Visit.VisitType.OPD, Visit.Status.WAITING_DOCTOR, appointment.getReason()
        );
        visit.setHospital(TenantScoping.currentHospitalReference(entityManager));
        visitRepository.save(visit);

        QueueTicket ticket = new QueueTicket(visit, nextNumber);
        ticket.setHospital(TenantScoping.currentHospitalReference(entityManager));
        queueTicketRepository.save(ticket);

        return new CheckInResponse(visit.getId(), nextNumber, visit.getStatus().name());
    }

    @Transactional(readOnly = true)
    public List<QueueTicketResponse> todaysQueue() {
        OffsetDateTime dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        return queueTicketRepository.findByCreatedAtBetweenAndServedFalseOrderByQueueNumberAsc(dayStart, dayEnd)
                .stream()
                .map(QueueTicketResponse::from)
                .toList();
    }
}
