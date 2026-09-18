package com.hms.doctor.dto;

import com.hms.entity.Drug;

/** Lookup row for the prescription-item drug picker — not stock's DrugSummary (that's a stock-quantity aggregate). */
public record DrugLookup(Long id, String name, String strength) {

    public static DrugLookup from(Drug drug) {
        return new DrugLookup(drug.getId(), drug.getName(), drug.getStrength());
    }
}
