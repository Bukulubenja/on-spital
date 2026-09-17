package com.hms.admin.dto;

/** A generic (label, count) row — used for the dashboard's status/department/role breakdowns. */
public record LabelCount(String label, long count) {
}
