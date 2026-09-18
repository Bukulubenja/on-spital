package com.hms.tenancy;

import com.hms.entity.Department;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.entity.Visit;
import com.hms.repository.DepartmentRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.VisitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hms.testsupport.EntityTestSupport.setField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the actual thing this SaaS-style rebuild is for — two hospitals'
 * staff, logins, and patient data are fully isolated from each other —
 * against real Postgres and the real Servlet filter chain (TenantResolvingFilter,
 * Spring Security, OpenEntityManagerInViewFilter), not mocks. Direct port
 * of the Django source's {@code hospital/tests.py}'s {@code TenantIsolationTests},
 * the one gap called out as open since Phase 1: every other test class in
 * this project mocks its repositories and only proves a workflow works
 * *within* a single tenant, never that two tenants can't see each other.
 *
 * <p>Fixture rows are created and torn down per test (not wrapped in a
 * rollback-per-test transaction) because the MockMvc-dispatched requests
 * run through {@code OpenEntityManagerInViewFilter}'s own request-scoped
 * persistence context, not the test method's — relying on Spring's
 * test-transaction rollback here would be fighting that filter rather than
 * exercising it honestly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TenantIsolationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HospitalRepository hospitalRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private VisitRepository visitRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${app.base-domain}")
    private String baseDomain;

    private Hospital hospitalA;
    private Hospital hospitalB;
    private User doctorA;
    private User doctorB;
    private User nurseA;
    private Patient patientA;
    private Patient patientB;
    private Department departmentB;
    private Visit visitB;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        hospitalA = persistHospital("Isolation Hospital A " + suffix, "iso-a-" + suffix);
        hospitalB = persistHospital("Isolation Hospital B " + suffix, "iso-b-" + suffix);

        doctorA = persistUser(hospitalA, "doctor1", "pass-a", User.Role.DOCTOR);
        nurseA = persistUser(hospitalA, "nurse1", "pass-a", User.Role.NURSE);
        patientA = persistPatient(hospitalA, "Hospital A Patient");

        doctorB = persistUser(hospitalB, "doctor1", "pass-b", User.Role.DOCTOR);
        departmentB = persistDepartment(hospitalB, "General Medicine");
        patientB = persistPatient(hospitalB, "Hospital B Patient");
        visitB = persistVisit(hospitalB, patientB, doctorB, departmentB, Visit.Status.WAITING_DOCTOR);
    }

    @AfterEach
    void tearDown() {
        visitRepository.delete(visitB);
        patientRepository.delete(patientA);
        patientRepository.delete(patientB);
        departmentRepository.delete(departmentB);
        userRepository.delete(doctorA);
        userRepository.delete(nurseA);
        userRepository.delete(doctorB);
        hospitalRepository.delete(hospitalA);
        hospitalRepository.delete(hospitalB);
    }

    private String hostFor(Hospital hospital) {
        return hospital.getSubdomain() + "." + baseDomain.split(":")[0];
    }

    private Hospital persistHospital(String name, String subdomain) throws Exception {
        var constructor = Hospital.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Hospital hospital = constructor.newInstance();
        setField(hospital, Hospital.class, "name", name);
        setField(hospital, Hospital.class, "subdomain", subdomain);
        setField(hospital, Hospital.class, "active", true);
        setField(hospital, Hospital.class, "createdAt", OffsetDateTime.now());
        return hospitalRepository.save(hospital);
    }

    private User persistUser(Hospital hospital, String username, String rawPassword, User.Role role) throws Exception {
        var constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        User user = constructor.newInstance();
        setField(user, User.class, "username", username);
        setField(user, User.class, "password", passwordEncoder.encode(rawPassword));
        setField(user, User.class, "role", role);
        setField(user, User.class, "active", true);
        user.setHospital(hospital);
        return userRepository.save(user);
    }

    private Department persistDepartment(Hospital hospital, String name) throws Exception {
        var constructor = Department.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Department department = constructor.newInstance();
        setField(department, Department.class, "name", name);
        setField(department, Department.class, "description", "");
        department.setHospital(hospital);
        return departmentRepository.save(department);
    }

    private Patient persistPatient(Hospital hospital, String fullName) {
        Patient patient = new Patient(
                fullName, Patient.Gender.FEMALE, LocalDate.of(1990, 1, 1), "555-0000", "", "", "", "");
        patient.setPatientNumber("P-ISO-" + UUID.randomUUID().toString().substring(0, 8));
        patient.setHospital(hospital);
        return patientRepository.save(patient);
    }

    private Visit persistVisit(Hospital hospital, Patient patient, User doctor, Department department, Visit.Status status) {
        Visit visit = new Visit(null, patient, doctor, department, Visit.VisitType.OPD, status, "");
        visit.setHospital(hospital);
        return visitRepository.save(visit);
    }

    @Test
    void sameUsernameInTwoHospitalsDoesNotCollide() {
        assertThat(doctorA.getId()).isNotEqualTo(doctorB.getId());
        assertThat(doctorA.getUsername()).isEqualTo(doctorB.getUsername());
    }

    @Test
    void loginAuthenticatesAgainstTheRightHospitalOnly() throws Exception {
        mockMvc.perform(get("/api/whoami")
                        .with(request -> {
                            request.setServerName(hostFor(hospitalA));
                            return request;
                        })
                        .with(httpBasic("doctor1", "pass-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalSubdomain").value(hospitalA.getSubdomain()));

        // Same username exists in Hospital B, but its password must not work
        // on Hospital A's subdomain — the two accounts are entirely distinct.
        mockMvc.perform(get("/api/whoami")
                        .with(request -> {
                            request.setServerName(hostFor(hospitalA));
                            return request;
                        })
                        .with(httpBasic("doctor1", "pass-b")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossHospitalVisitIs404NotForbidden() throws Exception {
        // visitB belongs to Hospital B. From Hospital A's subdomain it must be
        // invisible (404), not merely forbidden (403) — the tenantFilter scopes
        // the findById lookup itself, before the doctor-ownership check ever runs.
        mockMvc.perform(post("/api/visits/" + visitB.getId() + "/start")
                        .with(request -> {
                            request.setServerName(hostFor(hospitalA));
                            return request;
                        })
                        .with(httpBasic("doctor1", "pass-a")))
                .andExpect(status().isNotFound());
    }

    @Test
    void nurseQueueNeverSurfacesAnotherHospitalsVisit() throws Exception {
        mockMvc.perform(get("/api/nurse/queue")
                        .with(request -> {
                            request.setServerName(hostFor(hospitalA));
                            return request;
                        })
                        .with(httpBasic("nurse1", "pass-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.visitId == " + visitB.getId() + ")]").doesNotExist());
    }
}
