package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps to Django's existing hospital_vitalsigns table. */
@Entity
@Table(name = "hospital_vitalsigns")
public class VitalSigns extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(name = "pulse_rate", nullable = false)
    private int pulseRate;

    @Column(name = "blood_pressure", nullable = false, length = 20)
    private String bloodPressure;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal height;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id", nullable = true)
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    protected VitalSigns() {
        // JPA
    }

    public VitalSigns(Visit visit, BigDecimal temperature, int pulseRate, String bloodPressure,
                       BigDecimal weight, BigDecimal height, User recordedBy) {
        this.visit = visit;
        this.temperature = temperature;
        this.pulseRate = pulseRate;
        this.bloodPressure = bloodPressure;
        this.weight = weight;
        this.height = height;
        this.recordedBy = recordedBy;
    }

    public Visit getVisit() {
        return visit;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public int getPulseRate() {
        return pulseRate;
    }

    public String getBloodPressure() {
        return bloodPressure;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = OffsetDateTime.now();
        }
    }
}
