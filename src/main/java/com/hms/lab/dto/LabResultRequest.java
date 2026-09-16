package com.hms.lab.dto;

import jakarta.validation.constraints.NotBlank;

/** Mirrors LabResultForm's field set (hospital/forms.py). */
public record LabResultRequest(@NotBlank String resultValue, String normalRange, String remarks) {
}
