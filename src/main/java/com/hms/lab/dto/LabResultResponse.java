package com.hms.lab.dto;

/** alreadyRecorded mirrors Django's messages.info(...) "already recorded" path instead of erroring. */
public record LabResultResponse(String testName, boolean alreadyRecorded, String visitStatus) {
}
