package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Maps to Django's existing hospital_queueticket table. */
@Entity
@Table(name = "hospital_queueticket")
public class QueueTicket extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(name = "queue_number", nullable = false)
    private int queueNumber;

    @Column(nullable = false)
    private boolean served;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected QueueTicket() {
        // JPA
    }

    public QueueTicket(Visit visit, int queueNumber) {
        this.visit = visit;
        this.queueNumber = queueNumber;
        this.served = false;
    }

    public Visit getVisit() {
        return visit;
    }

    public int getQueueNumber() {
        return queueNumber;
    }

    public boolean isServed() {
        return served;
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
