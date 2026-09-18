package com.hms.reception;

import com.hms.reception.dto.AppointmentRequest;
import com.hms.reception.dto.AppointmentResponse;
import com.hms.reception.dto.CheckInResponse;
import com.hms.reception.dto.DepartmentSummary;
import com.hms.reception.dto.DoctorSummary;
import com.hms.reception.dto.PatientRequest;
import com.hms.reception.dto.PatientResponse;
import com.hms.reception.dto.PatientSummary;
import com.hms.reception.dto.QueueTicketResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Direct analogue of the RECEPTIONIST-only views in hospital/views.py.
 * @PreAuthorize is repeated per method (rather than once at class level) to
 * mirror Django's per-view @role_required decorator style, and to avoid
 * depending on class-level @PreAuthorize support specifically.
 */
@RestController
public class ReceptionController {

    private final ReceptionService receptionService;

    public ReceptionController(ReceptionService receptionService) {
        this.receptionService = receptionService;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @PostMapping("/api/patients")
    public ResponseEntity<PatientResponse> registerPatient(@Valid @RequestBody PatientRequest request) {
        var patient = receptionService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatientResponse.from(patient));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @PostMapping("/api/appointments")
    public ResponseEntity<AppointmentResponse> bookAppointment(@Valid @RequestBody AppointmentRequest request) {
        var appointment = receptionService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @PostMapping("/api/appointments/{id}/checkin")
    public CheckInResponse checkIn(@PathVariable("id") Long appointmentId) {
        return receptionService.checkIn(appointmentId);
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/api/reception/queue")
    public List<QueueTicketResponse> todaysQueue() {
        return receptionService.todaysQueue();
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/api/patients")
    public List<PatientSummary> searchPatients(@RequestParam(name = "q", required = false) String query) {
        return receptionService.searchPatients(query);
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/api/doctors")
    public List<DoctorSummary> listDoctors() {
        return receptionService.listDoctors();
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @GetMapping("/api/departments")
    public List<DepartmentSummary> listDepartments() {
        return receptionService.listDepartments();
    }
}
