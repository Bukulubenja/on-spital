package com.hms.cashier.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Mirrors InvoiceItemForm's field set (hospital/forms.py). */
public record InvoiceItemRequest(@NotNull Long serviceId, @Min(1) int quantity) {
}
