package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Maps to Django's existing hospital_prescriptionitem table. dispensed/
 * dispensedAt/dispensedBy are mapped now (needed for ddl-auto=validate
 * against a NOT NULL dispensed column) but unused until the Pharmacy phase.
 */
@Entity
@Table(name = "hospital_prescriptionitem")
public class PrescriptionItem extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 100)
    private String dosage;

    @Column(nullable = false, length = 100)
    private String frequency;

    @Column(nullable = false, length = 100)
    private String duration;

    @Column(nullable = false)
    private String instructions;

    @Column(nullable = false)
    private boolean dispensed;

    @Column(name = "dispensed_at", nullable = true)
    private OffsetDateTime dispensedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispensed_by_id", nullable = true)
    private User dispensedBy;

    protected PrescriptionItem() {
        // JPA
    }

    public PrescriptionItem(Prescription prescription, Drug drug, int quantity, String dosage,
                             String frequency, String duration, String instructions) {
        this.prescription = prescription;
        this.drug = drug;
        this.quantity = quantity;
        this.dosage = dosage;
        this.frequency = frequency;
        this.duration = duration;
        this.instructions = instructions == null ? "" : instructions;
        this.dispensed = false;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public Drug getDrug() {
        return drug;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getDosage() {
        return dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getDuration() {
        return duration;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isDispensed() {
        return dispensed;
    }

    public OffsetDateTime getDispensedAt() {
        return dispensedAt;
    }

    public User getDispensedBy() {
        return dispensedBy;
    }

    /** Mirrors dispense_prescription_item's final trio of field writes (services.py). */
    public void markDispensed(User pharmacist) {
        this.dispensed = true;
        this.dispensedAt = OffsetDateTime.now();
        this.dispensedBy = pharmacist;
    }
}
