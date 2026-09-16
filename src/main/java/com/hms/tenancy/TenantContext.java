package com.hms.tenancy;

/**
 * Holds the current request's hospital id for the life of the request.
 * Analogue of Django's {@code contextvars.ContextVar} in hospital/tenancy.py
 * — set by TenantResolvingFilter, read by TenantEntity's auto-populate and
 * by whatever enables the Hibernate tenant filter.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_HOSPITAL_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long hospitalId) {
        CURRENT_HOSPITAL_ID.set(hospitalId);
    }

    public static Long get() {
        return CURRENT_HOSPITAL_ID.get();
    }

    public static void clear() {
        CURRENT_HOSPITAL_ID.remove();
    }
}
