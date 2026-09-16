# HMS — Spring Boot Rebuild

A learning project: rebuilding the Django `HMS` hospital-management system in
Spring Boot, one workflow at a time, against the **same Postgres database**.
See the Django repo's `CLAUDE.md` for the original system this mirrors.

## Status: Phase 3 — Doctor workflow

### Phase 1 — Foundation (tenancy + auth)

Implements the multi-tenancy mechanism from `hospital/tenancy.py` +
`hospital/middleware.py` in Spring Boot/Hibernate terms, and proves it works
against real data with one endpoint.

- `com.hms.tenancy.TenantContext` — thread-local current hospital id (≈ Django's `ContextVar`)
- `com.hms.tenancy.TenantEntity` — mapped superclass with the tenant-scoping Hibernate `@Filter` (≈ `TenantModel`/`TenantManager`)
- `com.hms.tenancy.TenantResolvingFilter` — resolves the hospital from the request subdomain, gates the `X-Hospital-Subdomain` debug fallback (≈ `TenantMiddleware`)
- `com.hms.entity.{Hospital,User}` — mapped onto the existing `hospital_hospital`/`hospital_user` tables
- `com.hms.security.DjangoPbkdf2PasswordEncoder` — verifies Django's `pbkdf2_sha256$...` hashes directly, so existing logins work unchanged
- `GET /api/whoami` — proves auth + tenancy together

### Phase 2 — Reception (register patient, book appointment, check in)

Mirrors `hospital/views.py`'s `patient_create`/`appointment_create`/`appointment_checkin`
and `hospital/forms.py`'s `PatientForm`/`AppointmentForm`, field-for-field and
rule-for-rule.

- `com.hms.entity.{Department,Patient,Appointment,Visit,QueueTicket}` — mapped onto the existing tables of the same names
- `com.hms.tenancy.TenantScoping` — explicit `hospital`-assignment helper for new rows (see "Design notes" below for why this isn't automatic)
- `com.hms.reception.ReceptionService` — business logic, incl. the pessimistic-locked queue-numbering (`QueueTicketRepository.findFirstByCreatedAtBetweenOrderByQueueNumberDesc`, `@Lock(PESSIMISTIC_WRITE)`) that mirrors Django's `select_for_update()` in `appointment_checkin`
- `com.hms.reception.ReceptionController` — `POST /api/patients`, `POST /api/appointments`, `POST /api/appointments/{id}/checkin`, `GET /api/reception/queue`, each `@PreAuthorize("hasRole('RECEPTIONIST')")`

**Deliberate behavior improvement over the Django source**: patient-number
generation (`Patient.registerPatient`) uses a temporary unique placeholder
(`"TMP-" + UUID`) for the initial insert instead of Django's blank string,
which has a real (if narrow) race on concurrent registrations — see
`ReceptionService.registerPatient`'s comment. Final stored values are
identical (`P-000042`-style), so rows stay compatible across both apps.

### Phase 3 — Doctor (start consultation, vitals, diagnosis, prescribe, order labs, complete)

Mirrors `hospital/views.py`'s `visit_start`/`visit_record_vitals`/`visit_record_diagnosis`/
`visit_add_prescription_item`/`visit_add_lab_test`/`visit_complete`, plus
`hospital/permissions.py`'s `can_doctor_access`. First phase to actually
drive the `Visit` state machine forward.

- `com.hms.entity.{Drug,LabTest,VitalSigns,MedicalRecord,Prescription,PrescriptionItem,LabOrder,LabOrderItem}` — mapped onto the existing tables of the same names
- `com.hms.access.VisitAccess` — port of `permissions.py`'s `can_doctor_access`, deliberately kept as its own module (Nurse's `can_nurse_access` lands here too in a later phase)
- `com.hms.domain.VisitWorkflow` — port of `services.py`'s `visit_status_after_consultation`/`_after_lab` as pure functions (booleans in, `Visit.Status` out — directly unit-tested with no mocks)
- `com.hms.doctor.DoctorService`/`DoctorController` — `POST /api/visits/{id}/{start,vitals,diagnosis,prescriptions,lab-tests,complete}`, each `@PreAuthorize("hasRole('DOCTOR')")`. The current doctor comes from `SecurityContextHolder` → `HmsUserPrincipal`, not a request parameter — mirrors how `request.user` flows through Django's views.

`addLabTest` is idempotent (ordering the same test twice returns `alreadyOrdered: true` instead of erroring), matching Django's `get_or_create` + info-message path rather than `visit_add_prescription_item`'s plain create-another-row behavior for prescriptions.

## Design notes

- **`hospital` auto-population on insert is explicit, not automatic.**
  Django's `TenantModel.save()` does this for free; the idiomatic Hibernate
  equivalent (a `PreInsertEventListener`, replacing the now-deprecated
  `Interceptor.onSave()`) needs reaching into `SessionFactoryImpl`'s
  `EventListenerRegistry` in a way worth verifying against a running app
  rather than guessing. Until then, every entity-creating service method
  calls `TenantScoping.currentHospitalReference(entityManager)` once and
  sets it explicitly — one small helper, not framework magic.
- **Hibernate filters need a session bound before Spring Security runs**,
  which is why `spring.jpa.open-in-view` is `true` here (normally an
  anti-pattern) — see the comment in `application.yml`.

## Running it

Requires Java 17+ (no separate Maven/Gradle install needed — use the wrapper).

```
./mvnw spring-boot:run
```

Reads DB connection from environment variables — same names as the Django
`.env` so both apps can point at the same local Postgres:

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
BASE_DOMAIN   # e.g. lvh.me:8000 — must match the Django .env value
APP_DEBUG     # true locally, gates the X-Hospital-Subdomain header fallback
```

`spring.jpa.hibernate.ddl-auto` is pinned to `validate` — this app must
never alter the schema; that's Django migrations' job.

### Verifying against real data

```
curl -H "Host: <realsubdomain>.lvh.me:8000" \
     -u <existing-django-username>:<their-real-password> \
     http://localhost:8080/api/whoami

curl -H "Host: <realsubdomain>.lvh.me:8000" -u <receptionist>:<pass> \
     -X POST http://localhost:8080/api/patients \
     -H "Content-Type: application/json" \
     -d '{"fullName":"Jane Doe","gender":"FEMALE","dateOfBirth":"1990-01-01","phone":"555-0100"}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u <receptionist>:<pass> \
     -X POST http://localhost:8080/api/appointments \
     -H "Content-Type: application/json" \
     -d '{"patientId":1,"doctorId":<a real DOCTOR user id>,"departmentId":<a real department id>,"appointmentDate":"2026-09-20T10:00:00+00:00"}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u <receptionist>:<pass> \
     -X POST http://localhost:8080/api/appointments/1/checkin

curl -H "Host: <realsubdomain>.lvh.me:8000" -u <receptionist>:<pass> \
     http://localhost:8080/api/reception/queue
```

**Known blocker, still open**: the Django `.env`'s `DB_PASSWORD` does not
currently authenticate against the running local Postgres (`localhost:9999`)
— `manage.py shell` hits the same `password authentication failed for user
"postgres"` error, so this isn't something either app broke. Fix the
Django-side DB credentials first, then run the checks above.

## Tests

```
./mvnw test -Dtest='!HmsSpringBootApplicationTests'
```

(Excluding the Initializr-generated context-load test, which needs a real
DB connection — blocked by the same credential issue above.)

- `DjangoPbkdf2PasswordEncoderTests` — verifies against a hash generated by Django's actual `PBKDF2PasswordHasher`.
- `TenantResolvingFilterTests` — unit-level (mocked repository/session).
- `ReceptionServiceTests` — check-in guard clauses, pessimistic-lock queue numbering arithmetic, and patient-number formatting, all against mocked repositories.
- `DoctorServiceTests` — access-control guard clauses (wrong status, wrong doctor), queue-ticket marking, lab-test order idempotency, all against mocked repositories.
- `VisitWorkflowTests` — pure, no mocks; all branches of `afterConsultation`/`afterLab`.

Worth adding once a phase needs real concurrency/isolation proof: a full
`@SpringBootTest` against real Postgres mirroring Django's
`TenantIsolationTests` — not done yet since mocks were enough to prove the
logic in Phases 1–3.

## Next: Phase 4 — Lab or Pharmacy workflow

Both now have real data to act on (`visit_status_after_lab`, dispensing
against `PrescriptionItem`/`Stock`) — whichever the user picks first.
