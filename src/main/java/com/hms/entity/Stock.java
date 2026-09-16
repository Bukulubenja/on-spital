package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Maps to Django's existing hospital_stock table — one batch of a Drug. */
@Entity
@Table(name = "hospital_stock")
public class Stock extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    protected Stock() {
        // JPA
    }

    public Stock(Drug drug, int quantity, LocalDate expiryDate, String batchNumber) {
        this.drug = drug;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.batchNumber = batchNumber;
    }

    public Drug getDrug() {
        return drug;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getBatchNumber() {
        return batchNumber;
    }
}
