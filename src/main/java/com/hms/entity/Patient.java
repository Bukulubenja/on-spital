package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Maps to Django's existing hospital_patient table. */
@Entity
@Table(name = "hospital_patient")
public class Patient extends TenantEntity {

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    /**
     * Django stores the single-letter code ("M"/"F"/"O"), not the choice
     * name — @Enumerated(STRING) would write "MALE" and break Django's
     * reads, so this is a plain converter instead.
     */
    @Converter
    public static class GenderConverter implements AttributeConverter<Gender, String> {
        @Override
        public String convertToDatabaseColumn(Gender gender) {
            if (gender == null) {
                return null;
            }
            return switch (gender) {
                case MALE -> "M";
                case FEMALE -> "F";
                case OTHER -> "O";
            };
        }

        @Override
        public Gender convertToEntityAttribute(String code) {
            if (code == null) {
                return null;
            }
            return switch (code) {
                case "M" -> Gender.MALE;
                case "F" -> Gender.FEMALE;
                case "O" -> Gender.OTHER;
                default -> throw new IllegalArgumentException("Unknown gender code: " + code);
            };
        }
    }

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Convert(converter = GenderConverter.class)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    @Column(name = "patient_number", nullable = false, unique = true, length = 20)
    private String patientNumber;

    @Column(name = "emergency_contact_name", nullable = false, length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", nullable = false, length = 20)
    private String emergencyContactPhone;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Patient() {
        // JPA
    }

    public Patient(String fullName, Gender gender, LocalDate dateOfBirth, String phone, String address,
                    String bloodGroup, String emergencyContactName, String emergencyContactPhone) {
        this.fullName = fullName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.address = address == null ? "" : address;
        this.bloodGroup = bloodGroup == null ? "" : bloodGroup;
        this.emergencyContactName = emergencyContactName == null ? "" : emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone == null ? "" : emergencyContactPhone;
    }

    public String getFullName() {
        return fullName;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getPatientNumber() {
        return patientNumber;
    }

    public void setPatientNumber(String patientNumber) {
        this.patientNumber = patientNumber;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
