package com.hms.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Mirrors StockAdjustmentForm's field set (hospital/forms.py). The "batch
 * must belong to this drug" rule is Django's restricted ModelChoiceField
 * queryset (drug.stock_entries.all()); ported as a runtime ownership check
 * in StockManagerService since it depends on the path's drug id.
 */
public record AdjustStockRequest(
        @NotNull Long batchId,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 200) String reason
) {
}
