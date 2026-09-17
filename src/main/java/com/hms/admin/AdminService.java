package com.hms.admin;

import com.hms.admin.dto.AdminDashboardView;
import com.hms.admin.dto.AppointmentSeriesPoint;
import com.hms.admin.dto.LabelCount;
import com.hms.admin.dto.RecentPatientView;
import com.hms.admin.dto.RecentPaymentView;
import com.hms.admin.dto.RevenueSeriesPoint;
import com.hms.entity.Payment;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DrugRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.PaymentRepository;
import com.hms.repository.StockRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.VisitInvoiceRepository;
import com.hms.repository.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mirrors hospital/views.py's admin_dashboard, minus the parts that depend
 * on models this rebuild hasn't mapped yet (Ward/Bed/Admission, and the
 * Doctor/Nurse profile tables' department headcounts) — see the README's
 * Phase 9 notes. Read-only: unlike Reception/Doctor/Lab/Pharmacy/Cashier/
 * Nurse/Stock Manager, there's nothing here that mutates data, so there's no
 * AuditLog call site to port in this phase either.
 */
@Service
public class AdminService {

    private static final int TREND_WINDOW_DAYS = 14;

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final VisitInvoiceRepository visitInvoiceRepository;
    private final DrugRepository drugRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public AdminService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            VisitRepository visitRepository,
            PaymentRepository paymentRepository,
            VisitInvoiceRepository visitInvoiceRepository,
            DrugRepository drugRepository,
            StockRepository stockRepository,
            UserRepository userRepository
    ) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.visitRepository = visitRepository;
        this.paymentRepository = paymentRepository;
        this.visitInvoiceRepository = visitInvoiceRepository;
        this.drugRepository = drugRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardView dashboard() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime todayStart = today.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime todayEnd = todayStart.plusDays(1);
        OffsetDateTime yesterdayStart = todayStart.minusDays(1);

        long totalPatients = patientRepository.count();

        long todaysAppointments = appointmentRepository.countByAppointmentDateBetween(todayStart, todayEnd);
        long yesterdaysAppointments = appointmentRepository.countByAppointmentDateBetween(yesterdayStart, todayStart);

        long activeVisits = visitRepository.countByStatusNot(Visit.Status.COMPLETED);

        BigDecimal todaysRevenue = paymentRepository.sumAmountPaidBetween(todayStart, todayEnd);
        BigDecimal yesterdaysRevenue = paymentRepository.sumAmountPaidBetween(yesterdayStart, todayStart);

        BigDecimal outstandingBalance = visitInvoiceRepository.sumTotalAmount()
                .subtract(paymentRepository.sumAmountPaidTotal());

        long lowStockDrugs = drugRepository.findAllByOrderByNameAsc().stream()
                .filter(drug -> stockRepository.sumQuantityByDrug(drug) <= 0)
                .count();

        long totalStaff = userRepository.countByActiveTrueAndRoleNot(User.Role.PATIENT);

        LocalDate windowStart = today.minusDays(TREND_WINDOW_DAYS - 1L);
        OffsetDateTime windowStartDt = windowStart.atStartOfDay(zone).toOffsetDateTime();
        List<LocalDate> windowDates = zeroFilledDates(windowStart, today);

        Map<LocalDate, Long> appointmentsByDay = appointmentRepository
                .findByAppointmentDateBetween(windowStartDt, todayEnd).stream()
                .collect(Collectors.groupingBy(a -> a.getAppointmentDate().atZoneSameInstant(zone).toLocalDate(), Collectors.counting()));
        List<AppointmentSeriesPoint> appointmentsSeries = windowDates.stream()
                .map(date -> new AppointmentSeriesPoint(date, appointmentsByDay.getOrDefault(date, 0L)))
                .toList();

        Map<LocalDate, BigDecimal> revenueByDay = paymentRepository
                .findByPaymentDateBetween(windowStartDt, todayEnd).stream()
                .collect(Collectors.groupingBy(
                        p -> p.getPaymentDate().atZoneSameInstant(zone).toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmountPaid, BigDecimal::add)
                ));
        List<RevenueSeriesPoint> revenueSeries = windowDates.stream()
                .map(date -> new RevenueSeriesPoint(date, revenueByDay.getOrDefault(date, BigDecimal.ZERO)))
                .toList();

        List<LabelCount> visitsByStatus = visitRepository.countGroupedByStatus().stream()
                .map(row -> new LabelCount(((Visit.Status) row[0]).name(), (Long) row[1]))
                .toList();

        List<LabelCount> visitsByDepartment = visitRepository.countActiveGroupedByDepartment(Visit.Status.COMPLETED).stream()
                .map(row -> new LabelCount((String) row[0], (Long) row[1]))
                .toList();

        List<LabelCount> staffByRole = userRepository.countActiveGroupedByRoleExcluding(User.Role.PATIENT).stream()
                .map(row -> new LabelCount(((User.Role) row[0]).name(), (Long) row[1]))
                .toList();

        List<RecentPatientView> recentPatients = patientRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(RecentPatientView::from)
                .toList();
        List<RecentPaymentView> recentPayments = paymentRepository.findTop5ByOrderByPaymentDateDesc().stream()
                .map(RecentPaymentView::from)
                .toList();

        return new AdminDashboardView(
                totalPatients,
                todaysAppointments, percentDelta(todaysAppointments, yesterdaysAppointments),
                activeVisits,
                todaysRevenue, percentDelta(todaysRevenue, yesterdaysRevenue),
                outstandingBalance,
                lowStockDrugs,
                totalStaff,
                appointmentsSeries, revenueSeries,
                visitsByStatus, visitsByDepartment, staffByRole,
                recentPatients, recentPayments
        );
    }

    private static List<LocalDate> zeroFilledDates(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    /** Percentage change from previous to current — null (undefined) if previous is zero, mirrors Django's _percent_delta. */
    private static Integer percentDelta(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return Math.round(((current - previous) * 100f) / previous);
    }

    private static Integer percentDelta(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
