package com.hms.nurse;

import com.hms.access.VisitAccess;
import com.hms.doctor.dto.VitalsRequest;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.entity.VitalSigns;
import com.hms.nurse.dto.NurseQueueEntry;
import com.hms.repository.VisitRepository;
import com.hms.repository.VitalSignsRepository;
import com.hms.security.HmsUserPrincipal;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Mirrors hospital/views.py's nurse_dashboard/nurse_record_vitals and
 * hospital/permissions.py's can_nurse_access. Like Lab/Pharmacy/Cashier,
 * Nurse works a shared queue rather than a per-assignee one — any nurse can
 * triage any visit still waiting on a doctor.
 */
@Service
public class NurseService {

    private final VisitRepository visitRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final EntityManager entityManager;

    public NurseService(
            VisitRepository visitRepository,
            VitalSignsRepository vitalSignsRepository,
            EntityManager entityManager
    ) {
        this.visitRepository = visitRepository;
        this.vitalSignsRepository = vitalSignsRepository;
        this.entityManager = entityManager;
    }

    private static User currentNurse() {
        var principal = (HmsUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUser();
    }

    @Transactional(readOnly = true)
    public List<NurseQueueEntry> triageQueue() {
        return visitRepository.findByStatusOrderByVisitDateAsc(Visit.Status.WAITING_DOCTOR).stream()
                .map(visit -> NurseQueueEntry.from(visit, vitalSignsRepository.existsByVisit(visit)))
                .toList();
    }

    @Transactional
    public void recordVitals(Long visitId, VitalsRequest request) {
        Visit visit = TenantScoping.findByIdTenantScoped(entityManager, Visit.class, visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        User nurse = currentNurse();
        if (!VisitAccess.nurseCanAccess(nurse, visit)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This visit is not awaiting triage");
        }

        VitalSigns vitals = new VitalSigns(
                visit, request.temperature(), request.pulseRate(), request.bloodPressure(),
                request.weight(), request.height(), nurse
        );
        vitals.setHospital(TenantScoping.currentHospitalReference(entityManager));
        vitalSignsRepository.save(vitals);
    }
}
