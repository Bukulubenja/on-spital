package com.hms.cashier.dto;

import com.hms.entity.BillableService;

import java.math.BigDecimal;

public record ServiceSummary(Long id, String name, String serviceType, BigDecimal price) {

    public static ServiceSummary from(BillableService service) {
        return new ServiceSummary(service.getId(), service.getName(), service.getServiceType().name(), service.getPrice());
    }
}
