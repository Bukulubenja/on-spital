package com.hms.access;

import com.hms.entity.User;
import com.hms.entity.Visit;

/**
 * Object-level access checks, kept separate from services/controllers —
 * direct port of hospital/permissions.py, which the Django project
 * deliberately keeps as its own module for the same reason. Add
 * nurseCanAccess here (mirroring can_nurse_access) when the Nurse phase
 * needs it, rather than now.
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
}
