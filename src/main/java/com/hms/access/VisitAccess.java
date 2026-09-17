package com.hms.access;

import com.hms.entity.User;
import com.hms.entity.Visit;

/**
 * Object-level access checks, kept separate from services/controllers —
 * direct port of hospital/permissions.py, which the Django project
 * deliberately keeps as its own module for the same reason.
 */
public final class VisitAccess {

    private VisitAccess() {
    }

    /** True if this user is the doctor assigned to the visit and it's at a stage they can act on. */
    public static boolean doctorCanAccess(User user, Visit visit) {
        return user.getRole() == User.Role.DOCTOR
                && visit.getDoctor() != null
                && visit.getDoctor().getId().equals(user.getId())
                && (visit.getStatus() == Visit.Status.WAITING_DOCTOR || visit.getStatus() == Visit.Status.IN_CONSULTATION);
    }

    /**
     * True if this user is a nurse and the visit is still waiting on a doctor.
     * Unlike doctors, nurses aren't individually assigned to a visit — any
     * nurse can triage any patient waiting to be seen, same shared-pool model
     * used for lab/pharmacy/cashier.
     */
    public static boolean nurseCanAccess(User user, Visit visit) {
        return user.getRole() == User.Role.NURSE && visit.getStatus() == Visit.Status.WAITING_DOCTOR;
    }
}
