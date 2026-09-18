package com.hms.nurse;

import com.hms.doctor.dto.VitalsRequest;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.repository.VisitRepository;
import com.hms.repository.VitalSignsRepository;
import com.hms.security.HmsUserPrincipal;
import com.hms.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.hms.testsupport.EntityTestSupport.mockTenantScopedFind;
import static com.hms.testsupport.EntityTestSupport.setField;
import static com.hms.testsupport.EntityTestSupport.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NurseServiceTests {

    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final VitalSignsRepository vitalSignsRepository = mock(VitalSignsRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final NurseService service = new NurseService(visitRepository, vitalSignsRepository, entityManager);

    private User nurseUser;

    @BeforeEach
    void setUpTenantContextAndCurrentNurse() throws Exception {
        TenantContext.set(1L);
        var hospitalConstructor = Hospital.class.getDeclaredConstructor();
        hospitalConstructor.setAccessible(true);
        when(entityManager.getReference(Hospital.class, 1L)).thenReturn(hospitalConstructor.newInstance());

        nurseUser = newUser(6L, "nursejoy", User.Role.NURSE);
        var principal = new HmsUserPrincipal(nurseUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static User newUser(Long id, String username, User.Role role) throws Exception {
        var constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        User user = constructor.newInstance();
        setField(user, User.class, "username", username);
        setField(user, User.class, "password", "irrelevant");
        setField(user, User.class, "role", role);
        setField(user, User.class, "active", true);
        return withId(user, id);
    }

    private Visit waitingVisit() {
        Patient patient = new Patient("Jane Doe", Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1),
                "555-0100", "", "", "", "");
        return withId(new Visit(null, patient, null, null, Visit.VisitType.OPD, Visit.Status.WAITING_DOCTOR, ""), 20L);
    }

    private static VitalsRequest sampleVitals() {
        return new VitalsRequest(BigDecimal.valueOf(37.0), 72, "120/80", BigDecimal.valueOf(70), BigDecimal.valueOf(170));
    }

    @Test
    void triageQueueReportsWhetherVitalsAreAlreadyRecorded() {
        Visit visit = waitingVisit();
        when(visitRepository.findByStatusOrderByVisitDateAsc(Visit.Status.WAITING_DOCTOR)).thenReturn(List.of(visit));
        when(vitalSignsRepository.existsByVisit(visit)).thenReturn(true);

        List<com.hms.nurse.dto.NurseQueueEntry> queue = service.triageQueue();

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).visitId()).isEqualTo(20L);
        assertThat(queue.get(0).patientName()).isEqualTo("Jane Doe");
        assertThat(queue.get(0).hasVitals()).isTrue();
    }

    @Test
    void recordVitalsSucceedsForAVisitStillWaitingOnADoctor() {
        Visit visit = waitingVisit();
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        service.recordVitals(20L, sampleVitals());

        verify(vitalSignsRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordVitalsRejectsAVisitAlreadyPastTriage() {
        Visit visit = waitingVisit();
        setField(visit, Visit.class, "status", Visit.Status.IN_CONSULTATION);
        mockTenantScopedFind(entityManager, Visit.class, 20L, visit);

        assertThatThrownBy(() -> service.recordVitals(20L, sampleVitals()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not awaiting triage");
        verify(vitalSignsRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordVitalsRejectsWhenTheVisitDoesNotExist() {
        mockTenantScopedFind(entityManager, Visit.class, 20L, null);

        assertThatThrownBy(() -> service.recordVitals(20L, sampleVitals()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
