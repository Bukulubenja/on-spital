package com.hms.reception.dto;

import com.hms.entity.User;

/** Lookup row for the appointment-booking doctor picker — active DOCTOR-role users only. */
public record DoctorSummary(Long id, String username, String firstName, String lastName) {

    public static DoctorSummary from(User user) {
        return new DoctorSummary(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName());
    }
}
