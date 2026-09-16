package com.hms.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Mirrors VitalSignsForm's field set (hospital/forms.py). */
public record VitalsRequest(
        @NotNull @Positive BigDecimal temperature,
        @NotNull @Positive Integer pulseRate,
        @NotBlank String bloodPressure,
        @NotNull @Positive BigDecimal weight,
        @NotNull @Positive BigDecimal height
) {
}
