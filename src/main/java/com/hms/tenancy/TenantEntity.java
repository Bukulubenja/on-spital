package com.hms.tenancy;

import com.hms.entity.Hospital;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Abstract base for every tenant-scoped entity — analogue of Django's
 * TenantModel (hospital/tenancy.py). Every subclass gets a {@code hospital}
 * FK and is scoped by the {@code tenantFilter} Hibernate filter enabled
 * per-request by TenantResolvingFilter — the analogue of TenantManager
 * auto-scoping every queryset.
 *
 * <p>The filter condition is written to be null-safe (mirroring
 * {@code Model.objects.filter(hospital=get_current_hospital())}, which
 * filters to {@code hospital IS NULL} on the platform path where no tenant
 * is resolved) rather than assuming a hospital is always present.
 *
 * <p><b>Deliberately not implemented yet:</b> Django's TenantModel.save()
 * also auto-populates {@code hospital} from context if unset, so plain
 * {@code .create()}/{@code .save()} calls stay tenant-correct everywhere.
 * The idiomatic Hibernate equivalent is a {@code Interceptor} bean that
 * stamps the FK at flush time (entities can't have Spring beans injected
 * into lifecycle callbacks like {@code @PrePersist} directly). That's real
 * design work best done against a concrete subclass — there isn't one yet
 * in this phase (Patient/Appointment land in Phase 2) — so it's deferred
 * until then rather than built speculatively against nothing. Until it
 * exists, callers must set {@code hospital} explicitly before saving.
 *
 * <p>Known limitation, same as the Django design has none of: Hibernate
 * filters apply to root-entity queries and most lazy collection fetches,
 * but not to every JPQL join edge case — double-check any hand-written
 * JPQL/native query against a new entity actually respects tenant scope.
 */
@MappedSuperclass
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "hospitalId", type = Long.class)
)
@Filter(
        name = "tenantFilter",
        condition = "(:hospitalId IS NULL AND hospital_id IS NULL) OR (:hospitalId IS NOT NULL AND hospital_id = :hospitalId)"
)
public abstract class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    public Long getId() {
        return id;
    }

    public Hospital getHospital() {
        return hospital;
    }

    public void setHospital(Hospital hospital) {
        this.hospital = hospital;
    }
}
