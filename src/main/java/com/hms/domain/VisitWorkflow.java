package com.hms.domain;

import com.hms.entity.Visit;

/**
 * Port of hospital/services.py's visit_status_after_consultation /
 * visit_status_after_lab — pure routing logic, no repository/HTTP
 * awareness, same spirit as the Django functions. Callers pass in the
 * existence checks (rather than this class querying repositories itself)
 * since Django's version reads them off already-loaded relations
 * (visit.lab_orders.exists()) — same idea, just handed in explicitly.
 */
public final class VisitWorkflow {

    private VisitWorkflow() {
    }

    /**
     * Routes a visit onward once the doctor finishes with it: to the lab
     * if tests were ordered (lab takes precedence), to pharmacy if drugs
     * were prescribed, otherwise the visit is done.
     */
    public static Visit.Status afterConsultation(boolean hasLabOrders, boolean hasPrescriptions) {
        if (hasLabOrders) {
            return Visit.Status.WAITING_LAB;
        }
        if (hasPrescriptions) {
            return Visit.Status.WAITING_PHARMACY;
        }
        return Visit.Status.COMPLETED;
    }

    /** Routes a visit onward once the lab finishes: to pharmacy if drugs were prescribed, otherwise done. */
    public static Visit.Status afterLab(boolean hasPrescriptions) {
        return hasPrescriptions ? Visit.Status.WAITING_PHARMACY : Visit.Status.COMPLETED;
    }
}
