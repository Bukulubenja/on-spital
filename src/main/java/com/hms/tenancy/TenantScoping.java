package com.hms.tenancy;

import com.hms.entity.Hospital;
import jakarta.persistence.EntityManager;

/**
 * Explicit, DRY alternative to Django's TenantModel.save() auto-populate.
 * Call {@link #currentHospitalReference} once per entity-creating service
 * method and pass the result to setHospital(...) before saving — see
 * TenantEntity's Javadoc for why this is explicit rather than a Hibernate
 * event-listener hook.
 */
public final class TenantScoping {

    private TenantScoping() {
    }

    /**
     * A lazy, id-only reference — doesn't hit the database, just gives
     * Hibernate enough to write the hospital_id join column on insert.
     */
    public static Hospital currentHospitalReference(EntityManager entityManager) {
        Long hospitalId = TenantContext.get();
        if (hospitalId == null) {
            throw new IllegalStateException(
                    "No current hospital in TenantContext — this entity requires a tenant-scoped request.");
        }
        return entityManager.getReference(Hospital.class, hospitalId);
    }
}
