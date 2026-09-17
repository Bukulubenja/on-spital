package com.hms.stock;

import com.hms.entity.Drug;
import com.hms.entity.Stock;
import com.hms.entity.StockTransaction;
import com.hms.repository.DrugRepository;
import com.hms.repository.StockRepository;
import com.hms.repository.StockTransactionRepository;
import com.hms.stock.dto.AdjustStockRequest;
import com.hms.stock.dto.BatchView;
import com.hms.stock.dto.DrugStockDetailView;
import com.hms.stock.dto.DrugSummary;
import com.hms.stock.dto.ReceiveStockRequest;
import com.hms.stock.dto.StockDashboardView;
import com.hms.stock.dto.TransactionView;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Mirrors hospital/views.py's stock_dashboard/drug_stock_detail/
 * receive_stock/adjust_stock. Like Lab/Pharmacy/Cashier/Nurse, there's no
 * per-user ownership check — any stock manager can act on any drug's stock.
 */
@Service
public class StockManagerService {

    private static final int EXPIRY_WARNING_DAYS = 30;

    private final DrugRepository drugRepository;
    private final StockRepository stockRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final EntityManager entityManager;

    public StockManagerService(
            DrugRepository drugRepository,
            StockRepository stockRepository,
            StockTransactionRepository stockTransactionRepository,
            EntityManager entityManager
    ) {
        this.drugRepository = drugRepository;
        this.stockRepository = stockRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.entityManager = entityManager;
    }

    private Drug requireDrug(Long drugId) {
        return drugRepository.findById(drugId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"));
    }

    @Transactional(readOnly = true)
    public StockDashboardView dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(EXPIRY_WARNING_DAYS);

        var drugs = drugRepository.findAllByOrderByNameAsc().stream()
                .map(drug -> DrugSummary.from(drug, stockRepository.sumQuantityByDrug(drug)))
                .toList();

        long expiringSoonCount = stockRepository.countByQuantityGreaterThanAndExpiryDateBetween(0, today, soon);
        long expiredCount = stockRepository.countByQuantityGreaterThanAndExpiryDateLessThan(0, today);

        var recentTransactions = stockTransactionRepository.findTop20ByOrderByDateDesc().stream()
                .map(TransactionView::from)
                .toList();

        return new StockDashboardView(drugs, expiringSoonCount, expiredCount, recentTransactions);
    }

    @Transactional(readOnly = true)
    public DrugStockDetailView drugStockDetail(Long drugId) {
        Drug drug = requireDrug(drugId);
        LocalDate today = LocalDate.now();
        LocalDate expiryWarningDate = today.plusDays(EXPIRY_WARNING_DAYS);

        var batches = stockRepository.findByDrugOrderByExpiryDateAsc(drug).stream()
                .map(batch -> BatchView.from(batch, today, expiryWarningDate))
                .toList();
        var transactions = stockTransactionRepository.findTop20ByDrugOrderByDateDesc(drug).stream()
                .map(TransactionView::from)
                .toList();

        return new DrugStockDetailView(drug.getId(), drug.getName(), drug.getStrength(), batches, transactions);
    }

    @Transactional
    public void receiveStock(Long drugId, ReceiveStockRequest request) {
        Drug drug = requireDrug(drugId);
        if (!request.expiryDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date must be in the future");
        }

        stockRepository.findByDrugAndBatchNumber(drug, request.batchNumber())
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + request.quantity()),
                        () -> {
                            Stock created = new Stock(drug, request.quantity(), request.expiryDate(), request.batchNumber());
                            created.setHospital(TenantScoping.currentHospitalReference(entityManager));
                            stockRepository.save(created);
                        }
                );

        StockTransaction txn = new StockTransaction(
                drug, StockTransaction.TransactionType.IN, request.quantity(),
                "Received batch " + request.batchNumber()
        );
        txn.setHospital(TenantScoping.currentHospitalReference(entityManager));
        stockTransactionRepository.save(txn);
    }

    @Transactional
    public void adjustStock(Long drugId, AdjustStockRequest request) {
        Drug drug = requireDrug(drugId);
        Stock batch = stockRepository.findByIdForUpdate(request.batchId())
                .filter(candidate -> candidate.getDrug().getId().equals(drug.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));

        if (request.quantity() > batch.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot remove %d units — only %d left in batch %s"
                            .formatted(request.quantity(), batch.getQuantity(), batch.getBatchNumber()));
        }

        batch.setQuantity(batch.getQuantity() - request.quantity());
        StockTransaction txn = new StockTransaction(
                drug, StockTransaction.TransactionType.OUT, request.quantity(), request.reason()
        );
        txn.setHospital(TenantScoping.currentHospitalReference(entityManager));
        stockTransactionRepository.save(txn);
    }
}
