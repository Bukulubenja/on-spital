package com.hms.pharmacy.dto;

import java.util.List;

/** Read-only parity endpoint mirroring the read half of prescription_detail (hospital/views.py). */
public record PrescriptionView(boolean canDispense, List<Item> items) {

    public record Item(Long itemId, String drugName, int quantity, String dosage, String frequency,
                        String duration, boolean dispensed, int availableStock) {
    }
}
