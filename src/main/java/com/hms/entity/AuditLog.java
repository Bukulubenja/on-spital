package com.hms.entity;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Maps to Django's existing hospital_auditlog table. Write-only from this
 * app's side — mirrors hospital/services.py's record_audit_log, called by
 * the same four mutations Django's views.py calls it from (the other four
 * Django call sites are for EmergencyAlert/RefillRequest, features this
 * rebuild hasn't ported). {@code recordId} is deliberately {@code Integer},
 * not {@code Long}, matching Django's PositiveIntegerField column exactly
 * (a narrower type than every other entity's bigint id) rather than the
 * bigint every other entity in this codebase uses for its own id.
 */
@Entity
@Table(name = "hospital_auditlog")
public class AuditLog extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(nullable = false, length = 200)
    private String action;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "record_id", nullable = false)
    private Integer recordId;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(User user, String action, String tableName, Integer recordId, String ipAddress) {
        this.user = user;
        this.action = action;
        this.tableName = tableName;
        this.recordId = recordId;
        this.ipAddress = ipAddress;
    }

    public User getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getTableName() {
        return tableName;
    }

    public Integer getRecordId() {
        return recordId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = OffsetDateTime.now();
        }
    }
}
