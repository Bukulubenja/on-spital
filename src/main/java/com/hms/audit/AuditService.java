package com.hms.audit;

import com.hms.entity.AuditLog;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.tenancy.TenantScoping;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors hospital/services.py's record_audit_log. Unlike Django, where
 * every call site lives in views.py and services.py stays HTTP-free, the
 * write itself happens here in the service layer (immediately after the
 * mutation it records, on the same success path Django's callers use) —
 * callers still extract the client IP at the controller boundary and pass
 * it in as a plain String, so HttpServletRequest itself never reaches this
 * class or the service methods that call it.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;

    public AuditService(AuditLogRepository auditLogRepository, EntityManager entityManager) {
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void record(User user, String action, String tableName, Long recordId, String ipAddress) {
        AuditLog entry = new AuditLog(user, action, tableName, recordId == null ? null : recordId.intValue(), ipAddress);
        entry.setHospital(TenantScoping.currentHospitalReference(entityManager));
        auditLogRepository.save(entry);
    }
}
