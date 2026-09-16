package com.hms.pharmacy.dto;

/** alreadyDispensed mirrors Django's messages.info(...) "already dispensed" path instead of erroring. */
public record DispenseResponse(String drugName, int quantity, boolean alreadyDispensed, String visitStatus) {
}
