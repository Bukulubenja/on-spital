package com.hms.reception.dto;

import com.hms.entity.Patient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/** Mirrors PatientForm's field set exactly (hospital/forms.py). */
public record PatientRequest(
        @NotBlank String fullName,
        @NotNull Patient.Gender gender,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotBlank String phone,
        String address,
        String bloodGroup,
        String emergencyContactName,
        String emergencyContactPhone
) {
}
