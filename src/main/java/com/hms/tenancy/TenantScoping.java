package com.hms.tenancy;

import com.hms.entity.Hospital;
import jakarta.persistence.EntityManager;

import java.util.Optional;

/**
 * Explicit, DRY alternative to Django's TenantModel auto-scoping. Two
 * concerns live here: stamping {@code hospital} on new entities (mirroring
 * {@code TenantModel.save()}'s auto-populate), and reading a tenant-scoped
 * entity back out by id in a way that actually respects tenant boundaries
 * (mirroring {@code TenantManager}'s scoped {@code get_object_or_404}) —
 * see each method's own Javadoc for why neither is automatic here the way
 * it is in Django.
 */
public final class TenantScoping {

    private TenantScoping() {
    }

    /**
     * A lazy, id-only reference — doesn't hit the database, just gives
     * Hibernate enough to write the hospital_id join column on insert. Call
     * once per entity-creating service method and pass the result to
     * setHospital(...) before saving — see TenantEntity's Javadoc for why
     * this is explicit rather than a Hibernate event-listener hook.
     */
    public static Hospital currentHospitalReference(EntityManager entityManager) {
        Long hospitalId = TenantContext.get();
        if (hospitalId == null) {
            throw new IllegalStateException(
                    "No current hospital in TenantContext — this entity requires a tenant-scoped request.");
        }
        return entityManager.getReference(Hospital.class, hospitalId);
    }

    /**
     * Tenant-safe replacement for {@code JpaRepository.findById(id)} on any
     * {@link TenantEntity} subclass. Spring Data's {@code findById} compiles
     * to {@code EntityManager.find()} — a direct primary-key load that
     * Hibernate's {@code @Filter} mechanism does not apply to, unlike every
     * derived query method (which does go through a filtered JPQL query).
     * Confirmed live: a cross-hospital {@code Visit} was reachable via
     * {@code visitRepository.findById(id)} despite the tenantFilter being
     * correctly enabled for the request — see TenantIsolationTests. This
     * method runs an explicit JPQL {@code where e.id = :id} query instead,
     * which Hibernate does filter, closing that gap. {@code entityClass} is
     * always a compile-time class literal from the caller, never
     * user-controlled input, so interpolating its simple name into the JPQL
     * string is safe.
     */
    public static <T extends TenantEntity> Optional<T> findByIdTenantScoped(
            EntityManager entityManager, Class<T> entityClass, Long id
    ) {
        if (id == null) {
            return Optional.empty();
        }
        return entityManager
                .createQuery("select e from " + entityClass.getSimpleName() + " e where e.id = :id", entityClass)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }
}
