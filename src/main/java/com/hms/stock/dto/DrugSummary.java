package com.hms.stock.dto;

import com.hms.entity.Drug;

public record DrugSummary(Long drugId, String name, String category, String strength, int totalQuantity) {

    public static DrugSummary from(Drug drug, int totalQuantity) {
        return new DrugSummary(drug.getId(), drug.getName(), drug.getCategory(), drug.getStrength(), totalQuantity);
    }
}
