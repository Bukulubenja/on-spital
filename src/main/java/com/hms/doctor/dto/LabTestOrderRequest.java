package com.hms.doctor.dto;

import jakarta.validation.constraints.NotNull;

public record LabTestOrderRequest(@NotNull Long testId) {
}
