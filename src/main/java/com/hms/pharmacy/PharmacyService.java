package com.hms.pharmacy;

import com.hms.entity.Drug;
import com.hms.entity.Prescription;
import com.hms.entity.PrescriptionItem;
import com.hms.entity.Stock;
import com.hms.entity.StockTransaction;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.pharmacy.dto.DispenseResponse;
import com.hms.pharmacy.dto.PrescriptionView;
import com.hms.repository.PrescriptionItemRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.StockRepository;
import com.hms.repository.StockTransactionRepository;
import com.hms.repository.VisitRepository;
import com.hms.security.HmsUserPrincipal;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Mirrors hospital/views.py's dispense_item (and the read half of
 * prescription_detail) plus hospital/services.py's
 * dispense_prescription_item — FEFO stock deduction, rule-for-rule. Like
 * Lab, Pharmacy has no per-user ownership check: it works a shared queue.
 */
@Service
public class PharmacyService {

    private static final Set<Visit.Status> VIEWABLE_STATUSES = Set.of(Visit.Status.WAITING_PHARMACY, Visit.Status.COMPLETED);

    private final VisitRepository visitRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final StockRepository stockRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final EntityManager entityManager;

    public PharmacyService(
            VisitRepository visitRepository,
            PrescriptionRepository prescriptionRepository,
            PrescriptionItemRepository prescriptionItemRepository,
            StockRepository stockRepository,
            StockTransactionRepository stockTransactionRepository,
            EntityManager entityManager
    ) {
        this.visitRepository = visitRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.stockRepository = stockRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.entityManager = entityManager;
    }

    private static User currentPharmacist() {
        var principal = (HmsUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUser();
    }

    private Visit requireVisit(Long visitId) {
        return visitRepository.findById(visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
    }

    @Transactional
    public DispenseResponse dispensePrescriptionItem(Long visitId, Long itemId) {
        Visit visit = requireVisit(visitId);
        if (visit.getStatus() != Visit.Status.WAITING_PHARMACY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This visit is not awaiting pharmacy");
        }

        PrescriptionItem item = prescriptionItemRepository.findById(itemId)
                .filter(candidate -> candidate.getPrescription().getVisit().getId().equals(visit.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription item not found"));

        if (item.isDispensed()) {
            return new DispenseResponse(item.getDrug().getName(), item.getQuantity(), true, visit.getStatus().name());
        }

        Drug drug = item.getDrug();
        List<Stock> batches = stockRepository.findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc(drug, 0);
        int totalAvailable = batches.stream().mapToInt(Stock::getQuantity).sum();
        if (totalAvailable < item.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock to dispense " + drug.getName());
        }

        int remaining = item.getQuantity();
        for (Stock batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(batch.getQuantity(), remaining);
            batch.setQuantity(batch.getQuantity() - take);
            remaining -= take;
        }

        StockTransaction stockTransaction = new StockTransaction(
                drug, StockTransaction.TransactionType.OUT, item.getQuantity(),
                "Dispensed prescription item #" + item.getId());
        stockTransaction.setHospital(TenantScoping.currentHospitalReference(entityManager));
        stockTransactionRepository.save(stockTransaction);

        item.markDispensed(currentPharmacist());

        if (!prescriptionItemRepository.existsByPrescriptionAndDispensedFalse(item.getPrescription())) {
            visit.setStatus(Visit.Status.COMPLETED);
        }

        return new DispenseResponse(drug.getName(), item.getQuantity(), false, visit.getStatus().name());
    }

    @Transactional(readOnly = true)
    public PrescriptionView viewPrescription(Long visitId) {
        Visit visit = requireVisit(visitId);
        if (!VIEWABLE_STATUSES.contains(visit.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This visit is not awaiting pharmacy");
        }

        Prescription prescription = prescriptionRepository.findByVisit(visit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription not found"));

        var items = prescriptionItemRepository.findByPrescription(prescription).stream()
                .map(item -> new PrescriptionView.Item(
                        item.getId(), item.getDrug().getName(), item.getQuantity(), item.getDosage(),
                        item.getFrequency(), item.getDuration(), item.isDispensed(),
                        stockRepository.sumQuantityByDrug(item.getDrug())))
                .toList();

        return new PrescriptionView(visit.getStatus() == Visit.Status.WAITING_PHARMACY, items);
    }
}
