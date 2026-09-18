package com.hms.stock;

import com.hms.entity.Drug;
import com.hms.entity.Hospital;
import com.hms.entity.Stock;
import com.hms.entity.StockTransaction;
import com.hms.repository.DrugRepository;
import com.hms.repository.StockRepository;
import com.hms.repository.StockTransactionRepository;
import com.hms.stock.dto.AdjustStockRequest;
import com.hms.stock.dto.BatchView;
import com.hms.stock.dto.ReceiveStockRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hms.testsupport.EntityTestSupport.mockTenantScopedFind;
import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockManagerServiceTests {

    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final StockTransactionRepository stockTransactionRepository = mock(StockTransactionRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final StockManagerService service = new StockManagerService(
            drugRepository, stockRepository, stockTransactionRepository, entityManager
    );

    @BeforeEach
    void setUpTenantContext() throws Exception {
        com.hms.tenancy.TenantContext.set(1L);
        var hospitalConstructor = Hospital.class.getDeclaredConstructor();
        hospitalConstructor.setAccessible(true);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospitalConstructor.newInstance());
    }

    @AfterEach
    void clearContext() {
        com.hms.tenancy.TenantContext.clear();
    }

    private static Drug newDrug(Long id, String name, String category, String strength) throws Exception {
        var constructor = Drug.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Drug drug = constructor.newInstance();
        setField(drug, Drug.class, "name", name);
        setField(drug, Drug.class, "category", category);
        setField(drug, Drug.class, "strength", strength);
        return withId(drug, id);
    }

    @Test
    void dashboardAggregatesDrugTotalsAndExpiryCounts() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        when(drugRepository.findAllByOrderByNameAsc()).thenReturn(List.of(paracetamol));
        when(stockRepository.sumQuantityByDrug(paracetamol)).thenReturn(42);
        when(stockRepository.countByQuantityGreaterThanAndExpiryDateBetween(
                org.mockito.ArgumentMatchers.eq(0), any(), any())).thenReturn(2L);
        when(stockRepository.countByQuantityGreaterThanAndExpiryDateLessThan(
                org.mockito.ArgumentMatchers.eq(0), any())).thenReturn(1L);

        StockTransaction txn = new StockTransaction(paracetamol, StockTransaction.TransactionType.IN, 10, "Received batch A");
        when(stockTransactionRepository.findTop20ByOrderByDateDesc()).thenReturn(List.of(txn));

        var view = service.dashboard();

        assertThat(view.drugs()).hasSize(1);
        assertThat(view.drugs().get(0).totalQuantity()).isEqualTo(42);
        assertThat(view.expiringSoonCount()).isEqualTo(2L);
        assertThat(view.expiredCount()).isEqualTo(1L);
        assertThat(view.recentTransactions()).hasSize(1);
    }

    @Test
    void drugStockDetailComputesBatchStatusFromExpiryDate() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);

        LocalDate today = LocalDate.now();
        Stock expired = new Stock(paracetamol, 5, today.minusDays(1), "BATCH-EXPIRED");
        Stock expiringSoon = new Stock(paracetamol, 5, today.plusDays(10), "BATCH-SOON");
        Stock fresh = new Stock(paracetamol, 5, today.plusDays(90), "BATCH-FRESH");
        when(stockRepository.findByDrugOrderByExpiryDateAsc(paracetamol))
                .thenReturn(List.of(expired, expiringSoon, fresh));
        when(stockTransactionRepository.findTop20ByDrugOrderByDateDesc(paracetamol)).thenReturn(List.of());

        var view = service.drugStockDetail(3L);

        assertThat(view.batches()).extracting(BatchView::status)
                .containsExactly(BatchView.Status.EXPIRED, BatchView.Status.EXPIRING_SOON, BatchView.Status.FRESH);
    }

    @Test
    void receiveStockToBrandNewBatchCreatesAStockRowAndAnInTransaction() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);
        when(stockRepository.findByDrugAndBatchNumber(paracetamol, "BATCH-NEW")).thenReturn(Optional.empty());

        service.receiveStock(3L, new ReceiveStockRequest("BATCH-NEW", 20, LocalDate.now().plusDays(180)));

        verify(stockRepository).save(any(Stock.class));
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    @Test
    void receiveStockToAnExistingBatchTopsUpItsQuantityInsteadOfCreatingADuplicate() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);
        Stock existing = new Stock(paracetamol, 10, LocalDate.now().plusDays(180), "BATCH-EXISTING");
        when(stockRepository.findByDrugAndBatchNumber(paracetamol, "BATCH-EXISTING")).thenReturn(Optional.of(existing));

        service.receiveStock(3L, new ReceiveStockRequest("BATCH-EXISTING", 15, LocalDate.now().plusDays(180)));

        assertThat(existing.getQuantity()).isEqualTo(25);
        verify(stockRepository, never()).save(any(Stock.class));
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    @Test
    void receiveStockRejectsAnExpiryDateThatIsNotInTheFuture() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);

        assertThatThrownBy(() -> service.receiveStock(3L, new ReceiveStockRequest("BATCH-X", 5, LocalDate.now())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be in the future");
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    void adjustStockRejectsABatchThatBelongsToAnotherDrug() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        Drug ibuprofen = newDrug(4L, "Ibuprofen", "Analgesic", "200mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);
        Stock otherDrugsBatch = withId(new Stock(ibuprofen, 10, LocalDate.now().plusDays(10), "BATCH-OTHER"), 99L);
        when(stockRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(otherDrugsBatch));

        assertThatThrownBy(() -> service.adjustStock(3L, new AdjustStockRequest(99L, 5, "Spoiled")))
                .isInstanceOf(ResponseStatusException.class);
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    void adjustStockRejectsRemovingMoreThanIsLeftInTheBatch() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);
        Stock batch = withId(new Stock(paracetamol, 5, LocalDate.now().plusDays(10), "BATCH-A"), 99L);
        when(stockRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.adjustStock(3L, new AdjustStockRequest(99L, 6, "Spoiled")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot remove");
        assertThat(batch.getQuantity()).isEqualTo(5);
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    void adjustStockDeductsTheQuantityAndRecordsAnOutTransaction() throws Exception {
        Drug paracetamol = newDrug(3L, "Paracetamol", "Analgesic", "500mg");
        mockTenantScopedFind(entityManager, Drug.class, 3L, paracetamol);
        Stock batch = withId(new Stock(paracetamol, 20, LocalDate.now().plusDays(10), "BATCH-A"), 99L);
        when(stockRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(batch));

        service.adjustStock(3L, new AdjustStockRequest(99L, 6, "Spoiled"));

        assertThat(batch.getQuantity()).isEqualTo(14);
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }
}
