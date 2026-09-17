package com.hms.stock;

import com.hms.stock.dto.AdjustStockRequest;
import com.hms.stock.dto.DrugStockDetailView;
import com.hms.stock.dto.ReceiveStockRequest;
import com.hms.stock.dto.StockDashboardView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Direct analogue of the STOCK_MANAGER-only views in hospital/views.py acting on a Drug's stock. */
@RestController
public class StockManagerController {

    private final StockManagerService stockManagerService;

    public StockManagerController(StockManagerService stockManagerService) {
        this.stockManagerService = stockManagerService;
    }

    @PreAuthorize("hasRole('STOCK_MANAGER')")
    @GetMapping("/api/stock/dashboard")
    public StockDashboardView dashboard() {
        return stockManagerService.dashboard();
    }

    @PreAuthorize("hasRole('STOCK_MANAGER')")
    @GetMapping("/api/drugs/{id}/stock")
    public DrugStockDetailView drugStockDetail(@PathVariable("id") Long drugId) {
        return stockManagerService.drugStockDetail(drugId);
    }

    @PreAuthorize("hasRole('STOCK_MANAGER')")
    @PostMapping("/api/drugs/{id}/stock/receive")
    public ResponseEntity<Void> receiveStock(@PathVariable("id") Long drugId, @Valid @RequestBody ReceiveStockRequest request) {
        stockManagerService.receiveStock(drugId, request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('STOCK_MANAGER')")
    @PostMapping("/api/drugs/{id}/stock/adjust")
    public ResponseEntity<Void> adjustStock(@PathVariable("id") Long drugId, @Valid @RequestBody AdjustStockRequest request) {
        stockManagerService.adjustStock(drugId, request);
        return ResponseEntity.noContent().build();
    }
}
