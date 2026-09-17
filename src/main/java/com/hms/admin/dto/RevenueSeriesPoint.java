package com.hms.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueSeriesPoint(LocalDate date, BigDecimal amount) {
}
