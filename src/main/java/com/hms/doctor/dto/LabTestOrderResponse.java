package com.hms.doctor.dto;

/** alreadyOrdered mirrors Django's messages.info(...) "already ordered" path instead of erroring. */
public record LabTestOrderResponse(String testName, boolean alreadyOrdered) {
}
