package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Maps to Django's existing hospital_stocktransaction table — an audit trail row, never updated after insert. */
@Entity
@Table(name = "hospital_stocktransaction")
public class StockTransaction extends TenantEntity {

    public enum TransactionType {
        IN, OUT
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime date;

    protected StockTransaction() {
        // JPA
    }

    public StockTransaction(Drug drug, TransactionType type, int quantity, String reason) {
        this.drug = drug;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason == null ? "" : reason;
    }

    public Drug getDrug() {
        return drug;
    }

    public TransactionType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getDate() {
        return date;
    }

    @PrePersist
    protected void onCreate() {
        if (date == null) {
            date = OffsetDateTime.now();
        }
    }
}
