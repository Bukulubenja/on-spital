package com.hms.doctor;

import com.hms.access.VisitAccess;
import com.hms.audit.AuditService;
import com.hms.doctor.dto.DiagnosisRequest;
import com.hms.doctor.dto.DrugLookup;
import com.hms.doctor.dto.LabTestOrderResponse;
import com.hms.doctor.dto.LabTestSummary;
import com.hms.doctor.dto.PrescriptionItemRequest;
import com.hms.doctor.dto.VisitStatusResponse;
import com.hms.doctor.dto.VitalsRequest;
import com.hms.domain.VisitWorkflow;
import com.hms.entity.Drug;
import com.hms.entity.LabOrder;
import com.hms.entity.LabOrderItem;
import com.hms.entity.LabTest;
import com.hms.entity.MedicalRecord;
import com.hms.entity.Prescription;
import com.hms.entity.PrescriptionItem;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.entity.VitalSigns;
import com.hms.repository.DrugRepository;
import com.hms.repository.LabOrderItemRepository;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.LabTestRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.PrescriptionItemRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.QueueTicketRepository;
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
 * Mirrors hospital/views.py's visit_start/visit_record_vitals/
 * visit_record_diagnosis/visit_add_prescription_item/visit_add_lab_test/
 * visit_complete — see the Phase 3 plan for the rule-by-rule mapping.
 */
@Service
public class DoctorService {

    private final QueueTicketRepository queueTicketRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final DrugRepository drugRepository;
    private final LabTestRepository labTestRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public DoctorService(
            QueueTicketRepository queueTicketRepository,
            VitalSignsRepository vitalSignsRepository,
            MedicalRecordRepository medicalRecordRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionItemRepository prescriptionItemRepository,
            LabOrderRepository labOrderRepository,
            LabOrderItemRepository labOrderItemRepository,
            DrugRepository drugRepository,
            LabTestRepository labTestRepository,
            EntityManager entityManager,
            AuditService auditService
    ) {
        this.queueTicketRepository = queueTicketRepository;
        this.vitalSignsRepository = vitalSignsRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.labOrderRepository = labOrderRepository;
        this.labOrderItemRepository = labOrderItemRepository;
        this.drugRepository = drugRepository;
        this.labTestRepository = labTestRepository;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    private static User currentDoctor() {
        var principal = (HmsUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUser();
    }

    private Visit requireVisit(Long visitId) {
        return TenantScoping.findByIdTenantScoped(entityManager, Visit.class, visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
    }

    @Transactional
    public VisitStatusResponse startConsultation(Long visitId) {
        Visit visit = requireVisit(visitId);
        User doctor = currentDoctor();
        if (!VisitAccess.doctorCanAccess(doctor, visit) || visit.getStatus() != Visit.Status.WAITING_DOCTOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This visit cannot be started");
        }

        visit.setStatus(Visit.Status.IN_CONSULTATION);
        queueTicketRepository.findByVisit(visit).ifPresent(ticket -> ticket.setServed(true));

        return new VisitStatusResponse(visit.getId(), visit.getStatus().name());
    }

    private Visit requireActiveConsultation(Long visitId) {
        Visit visit = requireVisit(visitId);
        User doctor = currentDoctor();
        if (!VisitAccess.doctorCanAccess(doctor, visit) || visit.getStatus() != Visit.Status.IN_CONSULTATION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This action is only allowed during an active consultation");
        }
        return visit;
    }

    @Transactional
    public void recordVitals(Long visitId, VitalsRequest request) {
        Visit visit = requireActiveConsultation(visitId);
        VitalSigns vitals = new VitalSigns(
                visit, request.temperature(), request.pulseRate(), request.bloodPressure(),
                request.weight(), request.height(), currentDoctor()
        );
        vitals.setHospital(TenantScoping.currentHospitalReference(entityManager));
        vitalSignsRepository.save(vitals);
    }

    @Transactional
    public void recordDiagnosis(Long visitId, DiagnosisRequest request, String ipAddress) {
        Visit visit = requireActiveConsultation(visitId);
        User doctor = currentDoctor();
        MedicalRecord record = new MedicalRecord(
                visit, visit.getPatient(), doctor, request.diagnosis(), request.notes()
        );
        record.setHospital(TenantScoping.currentHospitalReference(entityManager));
        medicalRecordRepository.save(record);

        visit.setDiagnosisSummary(request.diagnosis());
        auditService.record(doctor, "RECORD_DIAGNOSIS", "hospital_medicalrecord", record.getId(), ipAddress);
    }

    @Transactional
    public void addPrescriptionItem(Long visitId, PrescriptionItemRequest request) {
        Visit visit = requireActiveConsultation(visitId);
        Drug drug = TenantScoping.findByIdTenantScoped(entityManager, Drug.class, request.drugId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"));

        Prescription prescription = prescriptionRepository.findByVisit(visit).orElseGet(() -> {
            Prescription created = new Prescription(visit, currentDoctor(), visit.getPatient());
            created.setHospital(TenantScoping.currentHospitalReference(entityManager));
            return prescriptionRepository.save(created);
        });

        PrescriptionItem item = new PrescriptionItem(
                prescription, drug, request.quantity(), request.dosage(),
                request.frequency(), request.duration(), request.instructions()
        );
        item.setHospital(TenantScoping.currentHospitalReference(entityManager));
        prescriptionItemRepository.save(item);
    }

    @Transactional
    public LabTestOrderResponse addLabTest(Long visitId, Long testId) {
        Visit visit = requireActiveConsultation(visitId);
        LabTest test = TenantScoping.findByIdTenantScoped(entityManager, LabTest.class, testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab test not found"));

        LabOrder labOrder = labOrderRepository.findByVisit(visit).orElseGet(() -> {
            LabOrder created = new LabOrder(visit, visit.getPatient(), currentDoctor());
            created.setHospital(TenantScoping.currentHospitalReference(entityManager));
            return labOrderRepository.save(created);
        });

        if (labOrderItemRepository.existsByLabOrderAndTest(labOrder, test)) {
            return new LabTestOrderResponse(test.getName(), true);
        }

        LabOrderItem item = new LabOrderItem(labOrder, test);
        item.setHospital(TenantScoping.currentHospitalReference(entityManager));
        labOrderItemRepository.save(item);
        return new LabTestOrderResponse(test.getName(), false);
    }

    @Transactional
    public VisitStatusResponse completeVisit(Long visitId) {
        Visit visit = requireActiveConsultation(visitId);
        boolean hasLabOrders = labOrderRepository.existsByVisit(visit);
        boolean hasPrescriptions = prescriptionRepository.existsByVisit(visit);
        visit.setStatus(VisitWorkflow.afterConsultation(hasLabOrders, hasPrescriptions));
        return new VisitStatusResponse(visit.getId(), visit.getStatus().name());
    }

    @Transactional(readOnly = true)
    public List<DrugLookup> listDrugs() {
        return drugRepository.findAllByOrderByNameAsc().stream().map(DrugLookup::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LabTestSummary> listLabTests() {
        return labTestRepository.findAllByOrderByNameAsc().stream().map(LabTestSummary::from).toList();
    }
}
