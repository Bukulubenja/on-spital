package com.hms.lab;

import com.hms.audit.AuditService;
import com.hms.domain.VisitWorkflow;
import com.hms.entity.LabOrder;
import com.hms.entity.LabOrderItem;
import com.hms.entity.LabResult;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.lab.dto.LabOrderView;
import com.hms.lab.dto.LabResultRequest;
import com.hms.lab.dto.LabResultResponse;
import com.hms.repository.LabOrderItemRepository;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.LabResultRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.security.HmsUserPrincipal;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mirrors hospital/views.py's record_lab_result (and the read half of
 * lab_order_detail) — see the Phase 4 plan for the rule-by-rule mapping.
 * Unlike Doctor, there's no per-user ownership check here: lab (like
 * pharmacy) works a shared queue, not a per-assignee one, matching
 * CLAUDE.md's description of the Django design.
 */
@Service
public class LabService {

    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabResultRepository labResultRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public LabService(
            LabOrderRepository labOrderRepository,
            LabOrderItemRepository labOrderItemRepository,
            LabResultRepository labResultRepository,
            PrescriptionRepository prescriptionRepository,
            EntityManager entityManager,
            AuditService auditService
    ) {
        this.labOrderRepository = labOrderRepository;
        this.labOrderItemRepository = labOrderItemRepository;
        this.labResultRepository = labResultRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    private static User currentLabUser() {
        var principal = (HmsUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUser();
    }

    @Transactional
    public LabResultResponse recordLabResult(Long visitId, Long labOrderItemId, LabResultRequest request, String ipAddress) {
        Visit visit = TenantScoping.findByIdTenantScoped(entityManager, Visit.class, visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        if (visit.getStatus() != Visit.Status.WAITING_LAB) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This visit is not awaiting lab work");
        }

        LabOrder labOrder = labOrderRepository.findByVisitForUpdate(visit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab order not found"));

        LabOrderItem item = labOrderItemRepository.findById(labOrderItemId)
                .filter(candidate -> candidate.getLabOrder().getId().equals(labOrder.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab order item not found"));

        if (labResultRepository.existsByLabOrderAndTest(labOrder, item.getTest())) {
            return new LabResultResponse(item.getTest().getName(), true, visit.getStatus().name());
        }

        LabResult result = new LabResult(labOrder, item.getTest(), request.resultValue(), request.normalRange(), request.remarks());
        result.setHospital(TenantScoping.currentHospitalReference(entityManager));
        labResultRepository.save(result);
        auditService.record(currentLabUser(), "RECORD_LAB_RESULT", "hospital_labresult", result.getId(), ipAddress);

        if (labOrder.getStatus() == LabOrder.Status.PENDING) {
            labOrder.setStatus(LabOrder.Status.PROCESSING);
        }

        boolean fullyResulted = labResultRepository.countByLabOrder(labOrder) >= labOrderItemRepository.countByLabOrder(labOrder);
        if (fullyResulted) {
            labOrder.setStatus(LabOrder.Status.COMPLETED);
            boolean hasPrescriptions = prescriptionRepository.existsByVisit(visit);
            visit.setStatus(VisitWorkflow.afterLab(hasPrescriptions));
        }

        return new LabResultResponse(item.getTest().getName(), false, visit.getStatus().name());
    }

    @Transactional(readOnly = true)
    public LabOrderView viewLabOrder(Long visitId) {
        Visit visit = TenantScoping.findByIdTenantScoped(entityManager, Visit.class, visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        LabOrder labOrder = labOrderRepository.findByVisit(visit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lab order not found"));

        var items = labOrderItemRepository.findByLabOrder(labOrder).stream()
                .map(item -> labResultRepository.findByLabOrderAndTest(labOrder, item.getTest())
                        .map(result -> new LabOrderView.Item(item.getId(), item.getTest().getName(), true, result.getResultValue()))
                        .orElseGet(() -> new LabOrderView.Item(item.getId(), item.getTest().getName(), false, null)))
                .toList();

        return new LabOrderView(labOrder.getStatus().name(), items);
    }
}
