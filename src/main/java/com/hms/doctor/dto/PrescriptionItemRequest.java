package com.hms.doctor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Mirrors PrescriptionItemForm's field set (hospital/forms.py). */
public record PrescriptionItemRequest(
        @NotNull Long drugId,
        @Min(1) int quantity,
        @NotBlank String dosage,
        @NotBlank String frequency,
        @NotBlank String duration,
        String instructions
) {
}
