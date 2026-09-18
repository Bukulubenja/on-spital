package com.hms.doctor.dto;

import com.hms.entity.LabTest;

import java.math.BigDecimal;

public record LabTestSummary(Long id, String name, BigDecimal price) {

    public static LabTestSummary from(LabTest test) {
        return new LabTestSummary(test.getId(), test.getName(), test.getPrice());
    }
}
