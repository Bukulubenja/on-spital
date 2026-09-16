# HMS — Spring Boot Rebuild

A learning project: rebuilding the Django `HMS` hospital-management system in
Spring Boot, one workflow at a time, against the **same Postgres database**.
See the Django repo's `CLAUDE.md` for the original system this mirrors.

## Status: Phase 5 — Pharmacy workflow

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
(`"TMP" + 8 hex chars`) for the initial insert instead of Django's blank
string, which has a real (if narrow) race on concurrent registrations — see
`ReceptionService.registerPatient`'s comment. Final stored values are
identical (`P-000042`-style), so rows stay compatible across both apps.
(First attempt used a full UUID — overflowed the `varchar(20)` column
Django defined for this field, caught only once live end-to-end testing
actually inserted a row; see "Bugs live testing caught" below.)

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

### Phase 4 — Lab (record results, complete lab orders)

Mirrors `hospital/views.py`'s `record_lab_result` (plus the read half of
`lab_order_detail`) and `hospital/services.py`'s `lab_order_fully_resulted`.
First phase to exercise `VisitWorkflow.afterLab` (added proactively back in
Phase 3, unused until now). Unlike Doctor, Lab has **no per-user ownership
check** — it works a shared queue, matching CLAUDE.md's description of the
Django design, so there's nothing to add to `VisitAccess` here.

- `com.hms.entity.LabResult` — mapped onto the existing `hospital_labresult` table
- `com.hms.lab.LabService`/`LabController` — `POST /api/visits/{id}/lab-order-items/{itemId}/result`, `GET /api/visits/{id}/lab-order`, both `@PreAuthorize("hasRole('LAB')")`
- "Fully resulted" check adapted as a count comparison (`labResultRepository.countByLabOrder(labOrder) >= labOrderItemRepository.countByLabOrder(labOrder)`) rather than Django's set-comparison — equivalent given the unique `(lab_order, test)` constraint and the per-test idempotency guard make duplicates impossible either way

Recording a result on an already-fully-resulted order's test correctly
returns `409` (matches Django: the `visit.status != WAITING_LAB` guard
runs *before* the idempotency check, so once the visit has moved on, no
further submissions are accepted — confirmed by live testing, not a bug).

### Phase 5 — Pharmacy (dispense prescriptions, FEFO stock deduction)

Mirrors `hospital/views.py`'s `dispense_item` (plus the read half of
`prescription_detail`) and `hospital/services.py`'s
`dispense_prescription_item` — first-expiry-first-out stock deduction
across a drug's batches, rule-for-rule. Like Lab, Pharmacy has no per-user
ownership check — it works a shared queue.

- `com.hms.entity.{Stock,StockTransaction}` — mapped onto the existing
  `hospital_stock`/`hospital_stocktransaction` tables. `Drug` was already
  mapped (read-only) since Phase 3.
- `com.hms.entity.PrescriptionItem.markDispensed(User)` — new mutator
  bundling the `dispensed`/`dispensedAt`/`dispensedBy` trio Django writes
  together in `dispense_prescription_item`.
- `com.hms.pharmacy.PharmacyService`/`PharmacyController` —
  `POST /api/visits/{id}/prescription-items/{itemId}/dispense`,
  `GET /api/visits/{id}/prescription`, both `@PreAuthorize("hasRole('PHARMACIST')")`.
- `StockRepository.findByDrugAndQuantityGreaterThanOrderByExpiryDateAsc`
  (`@Lock(PESSIMISTIC_WRITE)`) is the direct analogue of Django's
  `Stock.objects.select_for_update().filter(drug=..., quantity__gt=0).order_by("expiry_date")`
  — locks every in-stock batch of the drug, earliest expiry first, so
  concurrent dispenses of the same drug serialize against each other.
- Insufficient stock across all batches throws `409` and leaves every
  batch/item/visit untouched (Django's version just returns `False` and
  the view flashes an error — no partial deduction either way).
- Dispensing the last pending item on a prescription completes the visit
  (`Visit.Status.COMPLETED`) — this is a terminal state, not a
  `VisitWorkflow` routing decision, matching Django's `dispense_item` view
  setting `visit.status` directly rather than calling a `visit_status_after_*`
  function.

Dispensing an already-dispensed item is idempotent (`alreadyDispensed: true`,
no stock touched) exactly like Lab's `alreadyRecorded` — but the same
"status guard runs before the idempotency check" quirk from Phase 4 applies
here too: once the visit has moved on to `COMPLETED`, a repeat dispense call
on the same item now fails its `visit.status != WAITING_PHARMACY` guard
first and returns `409`, never reaching the idempotency branch. Confirmed
live (see below), matching Django's `dispense_item` exactly.

## Design notes

- **`hospital` auto-population on insert is explicit, not automatic.**
  Django's `TenantModel.save()` does this for free; the idiomatic Hibernate
  equivalent (a `PreInsertEventListener`, replacing the now-deprecated
  `Interceptor.onSave()`) needs reaching into `SessionFactoryImpl`'s
  `EventListenerRegistry` in a way worth verifying against a running app
  rather than guessing. Until then, every entity-creating service method
  calls `TenantScoping.currentHospitalReference(entityManager)` once and
  sets it explicitly — one small helper, not framework magic.
- **Hibernate filters need a session bound before Spring Security runs.**
  `spring.jpa.open-in-view` is deliberately `false` (its default `true`
  only auto-registers an MVC *interceptor*, which runs too late — see "Bugs
  live testing caught" below for why that doesn't work here). Instead,
  `SecurityConfig` registers a real `OpenEntityManagerInViewFilter` bean,
  positioned ahead of `TenantResolvingFilter`, so one Hibernate session is
  bound for the whole request.

## Bugs live testing caught

Both of these passed every unit test (mocked repositories can't catch
either class of bug) and only surfaced once the app was actually run
against real Postgres — the reason Phase 1's plan called out "verification"
as a distinct step, and worth internalizing for every future phase:

1. **Tenant filter silently not applied to real requests.** `open-in-view:
   true` only wires Spring Boot's MVC *interceptor*, which runs inside
   `DispatcherServlet` — after the Servlet filter chain (where
   `TenantResolvingFilter` lives) has already executed. `entityManager.unwrap(Session.class)`
   was therefore creating-and-immediately-closing a throwaway persistence
   context every request; the `tenantFilter` it enabled was never the one
   actually used downstream. Symptom: `LazyInitializationException: Could
   not initialize proxy [Hospital#7] - no session` the moment a controller
   touched a lazy association. Fixed by registering Spring's own
   `OpenEntityManagerInViewFilter` explicitly, ordered before
   `TenantResolvingFilter`, so a real session is bound for the whole
   request — see `SecurityConfig`.
2. **Patient-number placeholder too long for its own column.** `"TMP-" +
   UUID` is ~40 characters; Django's `patient_number` column is
   `varchar(20)`. First real `POST /api/patients` failed with
   `DataIntegrityViolationException: value too long for type character
   varying(20)`. Fixed by shortening the placeholder to `"TMP" + 8 hex
   chars` (~11 characters) — see `ReceptionService.registerPatient`.

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

**Done as of this session** — full Phase 1→5 flow run against the real
`stjohns` hospital in the live `HMS` database: `doctor1`, `reception1`,
`lab1`, and a new `pharm1` test user; a new `Patient`/`Appointment`/`Visit`
all the way through registration → check-in → consultation → lab order →
recorded result → `WAITING_PHARMACY` → **dispensed → `COMPLETED`**, then
cross-checked by reading the same rows back from Django's own ORM
(`manage.py shell`) at every stage — genuine proof both apps share live
data, not just a schema. Example commands:

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

curl -H "Host: <realsubdomain>.lvh.me:8000" -u doctor1:<pass> -X POST http://localhost:8080/api/visits/<id>/start
curl -H "Host: <realsubdomain>.lvh.me:8000" -u doctor1:<pass> -X POST http://localhost:8080/api/visits/<id>/complete

curl -H "Host: <realsubdomain>.lvh.me:8000" -u lab1:<pass> http://localhost:8080/api/visits/<id>/lab-order
curl -H "Host: <realsubdomain>.lvh.me:8000" -u lab1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/visits/<id>/lab-order-items/<itemId>/result \
     -d '{"resultValue":"5.2","normalRange":"4.0-6.0","remarks":"Within normal range"}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u pharm1:<pass> http://localhost:8080/api/visits/<id>/prescription
curl -H "Host: <realsubdomain>.lvh.me:8000" -u pharm1:<pass> \
     -X POST http://localhost:8080/api/visits/<id>/prescription-items/<itemId>/dispense
```

The Postgres credential issue that previously blocked this (`.env`'s
`DB_PASSWORD` not matching the running server) turned out to be exactly
that — Postgres runs as a native Windows service
(`postgresql-x64-18`, data dir `C:\Program Files\PostgreSQL\18\data`)
whose actual `postgres` role password had drifted from `.env`. Fixed by
briefly setting `pg_hba.conf` to `trust`, restarting the service (needs an
elevated terminal — `Restart-Service postgresql-x64-18 -Force`), running
`ALTER USER postgres WITH PASSWORD '...'` to match `.env`, then reverting
`pg_hba.conf` and restarting once more.

Local dev fixtures created in the `stjohns` hospital (id 7) for this
verification, in case anyone needs to redo it: `doctor1`'s password reset to
a known value, `reception1` (`RECEPTIONIST`), `lab1` (`LAB`), and `pharm1`
(`PHARMACIST`) test users, a `Drug` ("Paracetamol") and `LabTest` ("CBC")
row, and a `Stock` batch (50 units of Paracetamol, batch `SPRINGVERIFY-001`)
— see git history/session notes for the exact `manage.py shell` commands
used, not repeated here since they're throwaway local data, not part of
the app.

## Tests

```
./mvnw test
```

`HmsSpringBootApplicationTests` (the Initializr-generated context-load
test) needs a real DB connection and the `DB_*` env vars set — now that the
Postgres credential issue is fixed, this passes too; run it with the same
env vars as `spring-boot:run` above if running outside an IDE that already
has them configured.

- `DjangoPbkdf2PasswordEncoderTests` — verifies against a hash generated by Django's actual `PBKDF2PasswordHasher`.
- `TenantResolvingFilterTests` — unit-level (mocked repository/session).
- `ReceptionServiceTests` — check-in guard clauses, pessimistic-lock queue numbering arithmetic, and patient-number formatting, all against mocked repositories.
- `DoctorServiceTests` — access-control guard clauses (wrong status, wrong doctor), queue-ticket marking, lab-test order idempotency, all against mocked repositories.
- `VisitWorkflowTests` — pure, no mocks; all branches of `afterConsultation`/`afterLab`.
- `LabServiceTests` — status guard, result idempotency, `PENDING`→`PROCESSING` advancement, and both `afterLab` routing branches, all against mocked repositories.
- `PharmacyServiceTests` — status guard, dispense idempotency, FEFO batch deduction across multiple `Stock` rows, insufficient-stock rejection (no partial writes), and both dispense-completes-the-visit / items-still-pending outcomes, all against mocked repositories.

Worth adding once a phase needs real concurrency/isolation proof: a full
`@SpringBootTest` against real Postgres mirroring Django's
`TenantIsolationTests` — not done yet since mocks were enough to prove the
logic in Phases 1–5.

## Not in scope for this phase

Cashier, Nurse, Stock Manager, Admin.

## Next: Phase 6 — Cashier workflow

Billing a visit and collecting payment against `VisitInvoice`/`Payment` —
the visit from this session's verification (id 3) is now `COMPLETED` and
has a dispensed prescription, a reasonable candidate to bill against once
this phase exists.
