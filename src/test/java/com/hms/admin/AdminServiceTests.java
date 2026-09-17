package com.hms.admin;

import com.hms.entity.Drug;
import com.hms.entity.Patient;
import com.hms.entity.Payment;
import com.hms.entity.Visit;
import com.hms.entity.VisitInvoice;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DrugRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.PaymentRepository;
import com.hms.repository.StockRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.VisitInvoiceRepository;
import com.hms.repository.VisitRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminServiceTests {

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final VisitInvoiceRepository visitInvoiceRepository = mock(VisitInvoiceRepository.class);
    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final AdminService service = new AdminService(
            patientRepository, appointmentRepository, visitRepository, paymentRepository,
            visitInvoiceRepository, drugRepository, stockRepository, userRepository
    );

    private static Drug newDrug(Long id, String name) throws Exception {
        var constructor = Drug.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Drug drug = constructor.newInstance();
        setField(drug, Drug.class, "name", name);
        return withId(drug, id);
    }

    private void stubBaselineZeroes() {
        when(patientRepository.count()).thenReturn(0L);
        when(appointmentRepository.countByAppointmentDateBetween(any(), any())).thenReturn(0L);
        when(visitRepository.countByStatusNot(any())).thenReturn(0L);
        when(paymentRepository.sumAmountPaidBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(visitInvoiceRepository.sumTotalAmount()).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountPaidTotal()).thenReturn(BigDecimal.ZERO);
        when(drugRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(userRepository.countByActiveTrueAndRoleNot(any())).thenReturn(0L);
        when(appointmentRepository.findByAppointmentDateBetween(any(), any())).thenReturn(List.of());
        when(paymentRepository.findByPaymentDateBetween(any(), any())).thenReturn(List.of());
        when(visitRepository.countGroupedByStatus()).thenReturn(List.of());
        when(visitRepository.countActiveGroupedByDepartment(any())).thenReturn(List.of());
        when(userRepository.countActiveGroupedByRoleExcluding(any())).thenReturn(List.of());
        when(patientRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(paymentRepository.findTop5ByOrderByPaymentDateDesc()).thenReturn(List.of());
    }

    @Test
    void outstandingBalanceIsTotalBilledMinusTotalCollected() {
        stubBaselineZeroes();
        when(visitInvoiceRepository.sumTotalAmount()).thenReturn(new BigDecimal("500.00"));
        when(paymentRepository.sumAmountPaidTotal()).thenReturn(new BigDecimal("300.00"));

        var view = service.dashboard();

        assertThat(view.outstandingBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void appointmentsDeltaIsNullWhenYesterdayHadNone() {
        stubBaselineZeroes();
        when(appointmentRepository.countByAppointmentDateBetween(any(), any())).thenReturn(5L, 0L);

        var view = service.dashboard();

        assertThat(view.appointmentsDeltaPercent()).isNull();
    }

    @Test
    void revenueDeltaIsComputedAsAPercentageChange() {
        stubBaselineZeroes();
        when(paymentRepository.sumAmountPaidBetween(any(), any()))
                .thenReturn(new BigDecimal("150.00"))
                .thenReturn(new BigDecimal("100.00"));

        var view = service.dashboard();

        assertThat(view.revenueDeltaPercent()).isEqualTo(50);
    }

    @Test
    void lowStockDrugsCountsOnlyDrugsWithNoQuantityLeft() throws Exception {
        stubBaselineZeroes();
        Drug outOfStock = newDrug(1L, "Amoxicillin");
        Drug inStock = newDrug(2L, "Paracetamol");
        when(drugRepository.findAllByOrderByNameAsc()).thenReturn(List.of(outOfStock, inStock));
        when(stockRepository.sumQuantityByDrug(outOfStock)).thenReturn(0);
        when(stockRepository.sumQuantityByDrug(inStock)).thenReturn(40);

        var view = service.dashboard();

        assertThat(view.lowStockDrugs()).isEqualTo(1L);
    }

    @Test
    void visitsByStatusMapsGroupedRowsToLabelCounts() {
        stubBaselineZeroes();
        when(visitRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{Visit.Status.WAITING_DOCTOR, 3L},
                new Object[]{Visit.Status.COMPLETED, 7L}
        ));

        var view = service.dashboard();

        assertThat(view.visitsByStatus()).containsExactlyInAnyOrder(
                new com.hms.admin.dto.LabelCount("WAITING_DOCTOR", 3L),
                new com.hms.admin.dto.LabelCount("COMPLETED", 7L)
        );
    }

    @Test
    void trendSeriesIsZeroFilledForFourteenDaysIncludingToday() {
        stubBaselineZeroes();

        var view = service.dashboard();

        assertThat(view.appointmentsSeries()).hasSize(14);
        assertThat(view.appointmentsSeries().get(13).date()).isEqualTo(LocalDate.now());
        assertThat(view.appointmentsSeries()).allSatisfy(point -> assertThat(point.count()).isZero());
        assertThat(view.revenueSeries()).hasSize(14);
        assertThat(view.revenueSeries()).allSatisfy(point -> assertThat(point.amount()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void recentPaymentsResolveThePatientNameThroughTheInvoice() throws Exception {
        stubBaselineZeroes();
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        VisitInvoice invoice = withId(new VisitInvoice(null, patient, new BigDecimal("20.00")), 1L);
        Payment payment = withId(new Payment(invoice, new BigDecimal("20.00"), Payment.PaymentMethod.CASH, "ref"), 9L);
        setField(payment, Payment.class, "receiptNumber", "RCPT-000009");
        setField(payment, Payment.class, "paymentDate", OffsetDateTime.now());
        when(paymentRepository.findTop5ByOrderByPaymentDateDesc()).thenReturn(List.of(payment));

        var view = service.dashboard();

        assertThat(view.recentPayments()).hasSize(1);
        assertThat(view.recentPayments().get(0).patientName()).isEqualTo("Jane Doe");
        assertThat(view.recentPayments().get(0).receiptNumber()).isEqualTo("RCPT-000009");
    }
}
