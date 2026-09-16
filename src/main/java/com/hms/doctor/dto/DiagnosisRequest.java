package com.hms.doctor.dto;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisRequest(@NotBlank String diagnosis, String notes) {
}
