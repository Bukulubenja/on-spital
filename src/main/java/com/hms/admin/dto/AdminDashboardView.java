package com.hms.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors admin_dashboard's context (hospital/views.py) minus the parts that
 * depend on models this rebuild hasn't mapped yet — see the README's Phase 9
 * notes for what's deliberately left out (bed/ward occupancy, per-department
 * doctor/nurse headcounts) and why. Trend series are exposed as raw
 * (date, value) points rather than Django's chart.js-ready bar/line
 * structures, since this is a JSON API with no template to render into —
 * any client can build its own chart from the series.
 */
public record AdminDashboardView(
        long totalPatients,
        long todaysAppointments,
        Integer appointmentsDeltaPercent,
        long activeVisits,
        BigDecimal todaysRevenue,
        Integer revenueDeltaPercent,
        BigDecimal outstandingBalance,
        long lowStockDrugs,
        long totalStaff,
        List<AppointmentSeriesPoint> appointmentsSeries,
        List<RevenueSeriesPoint> revenueSeries,
        List<LabelCount> visitsByStatus,
        List<LabelCount> visitsByDepartment,
        List<LabelCount> staffByRole,
        List<RecentPatientView> recentPatients,
        List<RecentPaymentView> recentPayments
) {
}
