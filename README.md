# HMS — Spring Boot Rebuild

A learning project: rebuilding the Django `HMS` hospital-management system in
Spring Boot, one workflow at a time, against the **same Postgres database**.
See the Django repo's `CLAUDE.md` for the original system this mirrors.

## Status: Phase 11 — TenantIsolationTests (found and fixed a real cross-tenant IDOR)

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

### Phase 6 — Cashier (bill a visit, collect payment)

Mirrors `hospital/views.py`'s `visit_invoice_detail`/`add_invoice_item`/
`record_payment` and `hospital/services.py`'s `refresh_invoice_totals`.
Unlike Lab/Pharmacy, Cashier has **no visit-status gate at all** — a visit
can be billed at any point in its lifecycle (Django's `cashier_dashboard`
lists every visit, not a status-filtered queue), so `CashierService`
doesn't call into `VisitWorkflow` or check `visit.getStatus()` anywhere.

- `com.hms.entity.{VisitInvoice,InvoiceItem,Payment}` — mapped onto the
  existing `hospital_visitinvoice`/`hospital_invoiceitem`/`hospital_payment`
  tables. Django's `Service` model is ported as `com.hms.entity.BillableService`
  — named that instead of `Service` specifically to avoid colliding with
  Spring's own `@Service` stereotype annotation, which every workflow's
  service class already imports.
- `VisitInvoice.amountPaid`/`balanceDue` are Django `@property` values
  derived from summing the `payments` relation — computed in
  `CashierService` from `PaymentRepository.sumAmountPaidByInvoice`/
  `InvoiceItemRepository.sumSubtotalByInvoice` rather than mapped as
  entity fields, the same choice `LabOrder`/`PrescriptionItem` made for
  their own derived counts in earlier phases.
- `com.hms.cashier.CashierService`/`CashierController` —
  `GET /api/visits/{id}/invoice` (auto-creates an empty invoice on first
  view, mirroring Django's `get_or_create`), `POST /api/visits/{id}/invoice-items`,
  `POST /api/visits/{id}/payments`, all `@PreAuthorize("hasRole('CASHIER')")`.
- `VisitInvoiceRepository.findByVisitForUpdate` (`@Lock(PESSIMISTIC_WRITE)`)
  is the analogue of Django's `VisitInvoice.objects.select_for_update()` in
  `record_payment`. Unlike `viewInvoice`/`addInvoiceItem`, `recordPayment`
  does **not** auto-create the invoice — matching Django exactly, a payment
  against a visit with no invoice yet 404s rather than creating one.
- `Payment.receiptNumber` is `unique` + `NOT NULL` with no DB default, so
  it can't be known before the row has an id. Mirrors Django's two-step
  save (`payment.save()` with a blank receipt number, then
  `f"RCPT-{payment.pk:06d}"` and a second save) via
  `Payment.assignReceiptNumber()`, called right after
  `paymentRepository.save(payment)` generates the id.
- Recording a payment is idempotent on an already-fully-paid invoice
  (`alreadySettled: true`, no `Payment` row created) — same shape as Lab's
  `alreadyRecorded`/Pharmacy's `alreadyDispensed`. An amount exceeding the
  outstanding balance is rejected with `400` (Django's `clean_amount_paid`
  form validation, ported as a runtime check since it depends on the
  invoice's current balance, not a static bean-validation rule).

### Phase 7 — Nurse (pre-consultation triage vitals)

Mirrors `hospital/views.py`'s `nurse_dashboard`/`nurse_record_vitals` and
`hospital/permissions.py`'s `can_nurse_access`. Like Lab/Pharmacy/Cashier,
Nurse works a shared queue, not a per-assignee one — any nurse can triage
any visit still waiting on a doctor, so `VisitAccess.nurseCanAccess` only
checks role + `visit.status`, no ownership.

- `com.hms.nurse.NurseService`/`NurseController` —
  `GET /api/nurse/queue` (every `WAITING_DOCTOR` visit, oldest first,
  flagged with whether vitals are already on file — mirrors Django's
  `Exists(VitalSigns...)` annotation via `VitalSignsRepository.existsByVisit`),
  `POST /api/visits/{id}/nurse-vitals`, both `@PreAuthorize("hasRole('NURSE')")`.
- The vitals-recording endpoint deliberately lives at a different path than
  Doctor's `POST /api/visits/{id}/vitals` (`nurse-vitals`, not `vitals`) —
  same underlying `VitalSigns` row shape and request DTO
  (`com.hms.doctor.dto.VitalsRequest`, reused as-is since it's the same
  Django `VitalSignsForm` both roles fill in), but a different status gate
  (`WAITING_DOCTOR` for Nurse vs. `IN_CONSULTATION` for Doctor) and a
  different role, so a shared route would mean two `@PreAuthorize`s
  fighting over one mapping.
- Unlike Doctor's guard clauses, a rejected nurse triage doesn't advance or
  block anything else — recording vitals never changes `visit.status`, it's
  purely additive data the doctor reads once consultation starts.
- `VisitRepository.findByStatusOrderByVisitDateAsc` is the new query this
  phase needed (no prior phase listed visits by status); `VitalSignsRepository.existsByVisit`
  likewise.

### Phase 8 — Stock Manager (receive stock, write off spoiled/lost stock)

Mirrors `hospital/views.py`'s `stock_dashboard`/`drug_stock_detail`/
`receive_stock`/`adjust_stock`. `Stock`/`StockTransaction` were already
mapped in Phase 5 for Pharmacy's dispense path — this phase adds the write
side. Like Lab/Pharmacy/Cashier/Nurse, there's no per-user ownership check;
any stock manager can act on any drug's stock.

- `com.hms.stock.StockManagerService`/`StockManagerController` —
  `GET /api/stock/dashboard` (every drug with its summed quantity across
  batches, expiring-soon/expired batch counts, and the 20 most recent
  transactions hospital-wide), `GET /api/drugs/{id}/stock` (one drug's
  batches + its own 20 most recent transactions), `POST /api/drugs/{id}/stock/receive`,
  `POST /api/drugs/{id}/stock/adjust`, all `@PreAuthorize("hasRole('STOCK_MANAGER')")`.
- `BatchView.Status` (`EXPIRED`/`EXPIRING_SOON`/`FRESH`) ports
  `drug_stock_detail.html`'s date-comparison badge logic into a computed
  enum on the DTO rather than leaving the client to redo date arithmetic —
  same reasoning as other phases' computed response flags (`hasVitals`,
  `alreadyOrdered`, `alreadyDispensed`).
- `receiveStock` mirrors Django's `select_for_update().get_or_create(...)`:
  topping up an existing batch number's quantity in place, or inserting a
  new `Stock` row when the batch number is new to that drug — either way
  followed by an `IN` `StockTransaction`. The "expiry date must be in the
  future" rule is a runtime check (needs `LocalDate.now()`), not bean
  validation, same split `PaymentRequest`/`CashierService` made for the
  balance-dependent rule in Phase 6.
- `adjustStock` mirrors Django's `StockAdjustmentForm`'s restricted
  `ModelChoiceField` queryset (`drug.stock_entries.all()`) as a runtime
  ownership check — `StockRepository.findByIdForUpdate` locks the batch,
  then a filter confirms it actually belongs to the drug in the URL before
  any write, `404`ing otherwise. Removing more than a batch's current
  quantity `409`s and leaves the batch/transaction untouched, same shape as
  Phase 5's insufficient-stock guard.
- `StockRepository.findByDrugAndBatchNumber`/`findByIdForUpdate` are both
  `@Lock(PESSIMISTIC_WRITE)`, serializing concurrent receives/adjustments of
  the same batch — direct analogues of Django's two different
  `select_for_update()` call sites in these two views.

### Phase 9 — Admin (operational dashboard)

Mirrors `hospital/views.py`'s `admin_dashboard` — the only `ADMIN`-role view
in the Django source. Two scope decisions made up front (see the project's
own discussion before starting this phase):

- **No bed/ward occupancy, no per-department doctor/nurse headcounts.**
  Django's version pulls these from `Ward`/`Bed`/`Admission` and the
  `Doctor`/`Nurse` profile models (each with their own `department` FK) —
  an entire inpatient/staffing subsystem no phase has mapped, since this
  rebuild's `User` entity carries `role` directly with no profile table or
  department assignment. Porting those fields would mean standing up new
  domain, not just reading one more dashboard. Left out entirely rather
  than faked with zeros — an honest gap, not a silent behavior difference.
- **No `AuditLog`.** Django writes to it at mutations across every phase
  (diagnosis recorded, prescription dispensed, payment recorded, stock
  adjusted, etc.), but `admin_dashboard` itself is read-only — there's
  nothing for this phase to call `record_audit_log` from. Retrofitting it
  into Phases 1–8's services is a separate, larger effort, deliberately not
  bundled into this one.

- `com.hms.admin.AdminService`/`AdminController` — `GET /api/admin/dashboard`,
  `@PreAuthorize("hasRole('ADMIN')")`. Everything it reads was already
  mapped by Phases 1–8; this phase adds no new entities, only new
  aggregate queries.
- Django's `appointments_chart`/`revenue_chart` are chart.js-ready bar/line
  structures for server-rendered templates. Since this is a JSON API with
  no template, the 14-day trends are exposed as plain `(date, value)` point
  lists (`appointmentsSeries`/`revenueSeries`, zero-filled for days with no
  rows, matching Django's `_daily_series`) — any client can chart them
  itself. `visitsByStatus`/`visitsByDepartment`/`staffByRole` return the
  raw enum name as `label` (`"WAITING_DOCTOR"`, not Django's humanized
  `"Waiting Doctor"`), consistent with every other phase's JSON responses
  returning `.name()`, not a display string.
- `appointmentsDeltaPercent`/`revenueDeltaPercent` are `null` when
  yesterday's figure was zero (percentage change from zero is undefined),
  direct port of Django's `_percent_delta`.
- `visitsByDepartment` only counts non-`COMPLETED` visits, matching
  Django's `visits_by_department` — a department with only completed
  visits doesn't appear, same as the chart it feeds.
- New repository queries needed for this phase's aggregates:
  `VisitRepository.{countByStatusNot, countGroupedByStatus,
  countActiveGroupedByDepartment}`, `UserRepository.{countByActiveTrueAndRoleNot,
  countActiveGroupedByRoleExcluding}`, `PaymentRepository.{sumAmountPaidBetween,
  sumAmountPaidTotal, findByPaymentDateBetween, findTop5ByOrderByPaymentDateDesc}`,
  `AppointmentRepository.{countByAppointmentDateBetween, findByAppointmentDateBetween}`,
  `VisitInvoiceRepository.sumTotalAmount`, `PatientRepository.findTop5ByOrderByCreatedAtDesc`.
  All read-only, no new locking needed since nothing here writes.

### Phase 10 — AuditLog

Mirrors `hospital/services.py`'s `record_audit_log` and the four (of its
eight total) `hospital/views.py` call sites that map to workflows this
rebuild actually has: `RECORD_DIAGNOSIS` (Doctor), `RECORD_LAB_RESULT`
(Lab), `DISPENSE_PRESCRIPTION_ITEM` (Pharmacy), `RECORD_PAYMENT` (Cashier).
The other four Django call sites (`ACKNOWLEDGE_EMERGENCY_ALERT`,
`RESOLVE_EMERGENCY_ALERT`, `APPROVE_REFILL_REQUEST`, `DENY_REFILL_REQUEST`)
are for `EmergencyAlert`/`RefillRequest` features no phase has ported, so
there's nothing to wire them into. Nurse, Stock Manager, and Reception
mutations are **not** audited — confirmed against the Django source, not
assumed: none of `nurse_record_vitals`, `receive_stock`/`adjust_stock`, or
`patient_create`/`appointment_create`/`appointment_checkin` call
`record_audit_log` either.

- `com.hms.entity.AuditLog` — mapped onto the existing `hospital_auditlog`
  table (verified against the live schema before writing the entity, since
  `ddl-auto=validate` gives zero tolerance for a mismatch). `recordId` is
  deliberately `Integer`, not `Long` — Django's column is a
  `PositiveIntegerField`, narrower than every other entity's `bigint` id,
  and mirroring it field-for-field means keeping that narrowness rather
  than "fixing" it. `ipAddress` uses Hibernate 7's `@JdbcTypeCode(SqlTypes.INET)`
  on a `String` field to map onto Postgres's native `inet` column type —
  confirmed working live (`curl` from `localhost` stored as `::1`).
- `com.hms.audit.AuditService` — one shared `record(user, action,
  tableName, recordId, ipAddress)` method, injected into
  `DoctorService`/`LabService`/`PharmacyService`/`CashierService`. Unlike
  Django, where every `record_audit_log` call site lives in `views.py` and
  `services.py` stays free of HTTP objects, the write itself happens here
  in the Spring service layer (immediately after the mutation it records,
  on the same success path Django's callers use) — controllers still
  extract the client IP at the HTTP boundary via `com.hms.audit.ClientIp`
  (a direct port of `views.py`'s `_client_ip`) and pass it down as a plain
  `String`, so `HttpServletRequest` itself never reaches the service layer.
  A deliberate, documented deviation from Django's exact file boundaries,
  not from its behavior.
- Fires only on the real mutation path, never the idempotent early-return
  branches (`alreadyRecorded`/`alreadyDispensed`/`alreadySettled`) — matches
  Django's `record_audit_log` calls, which likewise sit after the "already
  done" guard clauses, not before them. Confirmed both live (a repeat
  payment against a fully-settled invoice writes no second row) and in
  each service's test suite.
- `LabService`/`CashierService` gained a `currentLabUser()`/`currentCashier()`
  helper (same `SecurityContextHolder` → `HmsUserPrincipal` pattern
  `DoctorService`/`PharmacyService` already used) — neither service
  previously needed to know who was calling it, since Lab/Cashier have no
  per-user ownership check; the audit log is the first thing in either
  workflow that cares who acted, not just that the action happened.

### Phase 11 — TenantIsolationTests (found and fixed a real cross-tenant IDOR)

Direct port of the Django source's `hospital/tests.py`'s `TenantIsolationTests`
— the one gap called out as open since Phase 1: every other test class in
this project mocks its repositories, proving each workflow works *within*
a tenant but never that two tenants can't see each other. This phase wrote
that test for real, against real Postgres and the real Servlet filter
chain (`TenantResolvingFilter`, Spring Security, `OpenEntityManagerInViewFilter`)
— and it immediately found a genuine, exploitable vulnerability, not a
hypothetical one.

**The bug.** Hibernate's `@Filter`-based tenant scoping — the mechanism
behind every `hospital-a.lvh.me` request being unable to see
`hospital-b.lvh.me`'s data — does not apply to Spring Data's
`findById(id)`. `findById` compiles to `EntityManager.find()`, a direct
primary-key load that bypasses Hibernate's filter machinery entirely; only
derived-query methods (`findByStatus`, `findByVisit`, etc.) actually run
through a filtered JPQL query. Proof, live: authenticated as one hospital's
doctor, `POST /api/visits/{anotherHospitalsVisitId}/start` returned `409`
("This visit cannot be started") — meaning the visit *was found* — instead
of the `404` a correctly tenant-scoped lookup should give, while
`GET /api/nurse/queue` (a derived-query method) correctly excluded the
same cross-hospital visit in the same test run.

- `com.hms.tenancy.TenantScoping.findByIdTenantScoped(EntityManager, Class<T>, Long)`
  — the fix: an explicit JPQL `select e from <Type> e where e.id = :id`
  query, which Hibernate's filter *does* apply to, replacing
  `repository.findById(id)` everywhere a tenant-scoped entity's "not found"
  result needs to mean "not visible to this tenant," not just "no row with
  that id anywhere." `entityClass.getSimpleName()` is interpolated into the
  JPQL string, safe because callers always pass a compile-time class
  literal, never user input.
- **14 call sites across 7 services** replaced: `DoctorService` (Visit ×1,
  Drug, LabTest), `NurseService` (Visit), `LabService` (Visit ×2),
  `PharmacyService` (Visit), `CashierService` (Visit, BillableService),
  `ReceptionService` (Patient, User, Department, Appointment),
  `StockManagerService` (Drug). Two `findById` call sites were deliberately
  **left unchanged** — `LabService`'s `labOrderItemRepository.findById`
  and `PharmacyService`'s `prescriptionItemRepository.findById` — because
  each is already safe by construction: both cross-check the looked-up
  item against a `LabOrder`/`Visit` that was *itself* already fetched
  through a tenant-safe query first, so a cross-tenant id can't pass the
  follow-up `.filter(...)` either way.
- Several services lost repository fields/constructor parameters entirely
  (`DoctorService` no longer takes `VisitRepository`/`DrugRepository`/
  `LabTestRepository`; `CashierService` no longer takes
  `VisitRepository`/`BillableServiceRepository`; `PharmacyService` no
  longer takes `VisitRepository`; `ReceptionService` no longer takes
  `DepartmentRepository`/`UserRepository`) once their only use was the now-removed
  `findById` call — deleted rather than left as dead fields, per this
  project's own no-dead-code convention.
- `com.hms.testsupport.EntityTestSupport.mockTenantScopedFind(EntityManager, Class<T>, Long, T)`
  — new shared test helper mocking the `entityManager.createQuery(...).setParameter(...).getResultStream()`
  chain `findByIdTenantScoped` runs under the hood, since a mocked
  `EntityManager` needs that chain stubbed instead of a simple
  `when(repository.findById(id)).thenReturn(...)`. Every existing test
  that previously stubbed a now-fixed `findById` call was updated to use
  it instead (`DoctorServiceTests`, `NurseServiceTests`, `LabServiceTests`,
  `CashierServiceTests`, `PharmacyServiceTests`, `ReceptionServiceTests`,
  `StockManagerServiceTests`).
- `com.hms.entity.User` gained six previously-unmapped columns
  (`is_superuser`, `is_staff`, `first_name`, `last_name`, `email`,
  `date_joined`) — a second bug this phase's own test setup surfaced.
  Every prior phase only ever *read* pre-existing Django-created `User`
  rows; `TenantIsolationTests` is the first code in this rebuild to
  actually `INSERT` a `User` via Hibernate (creating cross-hospital test
  fixtures), which immediately failed on Postgres's `is_superuser NOT NULL`
  constraint — a column with no DB-level default (Django enforces
  `False`/`""`/`now()` at the app layer only). Mapped with the same Django
  defaults `AbstractUser` uses, still with no public setters since nothing
  outside test fixtures needs to change them.
- `com.hms.tenancy.TenantIsolationTests` itself creates and tears down real
  `Hospital`/`User`/`Patient`/`Department`/`Visit` rows per test (not
  wrapped in a rollback-per-test transaction) via `MockMvc` +
  `@SpringBootTest(webEnvironment = MOCK)` — real HTTP Basic auth, real
  `Host`-header tenant resolution (`request.setServerName(...)` via a
  `RequestPostProcessor`, since MockMvc doesn't parse a literal `Host`
  header into `getServerName()` the way a real servlet container does),
  real Servlet filter chain. Four cases, each a direct port of the Django
  test of the same name: same username colliding across two hospitals
  (doesn't — different ids), login only authenticating against the
  correct hospital, a cross-hospital visit lookup returning `404` not
  `403`/`409`, and a cross-hospital visit never appearing in a
  tenant-scoped list endpoint (`GET /api/nurse/queue`, this rebuild's
  closest analogue to Django's `patient_list`, since no all-patients
  listing endpoint exists here).

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

All three passed every unit test (mocked repositories can't catch any of
these) and only surfaced once the app was actually run against real
Postgres — the reason Phase 1's plan called out "verification" as a
distinct step, and worth internalizing for every future phase:

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
3. **Cross-tenant IDOR via `findById` bypassing Hibernate's `@Filter`.**
   The most serious of the three — see Phase 11 above for the full
   writeup. `visitRepository.findById(id)` (and 13 other tenant-scoped
   `findById` calls) never actually applied the `tenantFilter`, so an
   authenticated user in one hospital could act on another hospital's
   data just by guessing/incrementing a numeric id. Confirmed live: a
   cross-hospital `POST /api/visits/{id}/start` returned `409` (found,
   wrong status) instead of `404` (should be invisible) before the fix,
   `404` after. Fixed via `TenantScoping.findByIdTenantScoped`, an
   explicit JPQL query that Hibernate's filter does apply to, replacing
   every `findById` call where "not found" needs to mean "not visible to
   this tenant."

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

**Done as of this session (Phase 10)** — a fresh patient run through
register → checkin → start consultation → `POST /api/visits/{id}/diagnosis`
against the live `stjohns` hospital wrote a real `hospital_auditlog` row:
`action: RECORD_DIAGNOSIS`, `table_name: hospital_medicalrecord`,
`record_id` matching the new `MedicalRecord`'s id, `user_id` matching
`doctor1`'s id, `hospital_id: 7`, and `ip_address: ::1` (confirming the
`@JdbcTypeCode(SqlTypes.INET)` mapping round-trips through Postgres's
native `inet` column correctly, not just as an opaque string). A follow-up
`POST /api/visits/{id}/payments` on the same visit wrote a second row
(`RECORD_PAYMENT` / `hospital_payment`, `user_id` matching `cashier1`'s
id) — confirmed both mutations fire, with the correct actor, table, and
record each time. `ddl-auto=validate` also passed at startup and in
`HmsSpringBootApplicationTests`, which is the real proof the `AuditLog`
entity's column mapping is byte-for-byte correct against the live
`hospital_auditlog` schema Django's migrations created — a validate-mode
mismatch would have failed the app at boot, not silently produced wrong
data.

**Done in a prior session (Phase 9)** — `GET /api/admin/dashboard` run
against the live `stjohns` hospital, and every figure cross-checked by hand
against this project's own test history: `totalPatients: 3` and
`recentPatients` listing all three in the right order; `activeVisits: 1`
and `visitsByStatus` showing `IN_CONSULTATION: 1`/`COMPLETED: 1` (the two
real `Visit` rows created across every phase's live testing so far);
`visitsByDepartment` correctly excluding the completed visit and showing
only the in-progress one's department; `revenueDeltaPercent: -100` (the
prior day's Cashier-phase payments summing to 40.00, today's summing to 0 —
`(0-40)/40*100`, checks out); `recentPayments` showing both Phase 6
payments with the patient name correctly resolved through the invoice;
`staffByRole` summing to `totalStaff: 9`, matching every test user created
across Phases 1–9; `lowStockDrugs: 0` (Paracetamol still has stock); and a
non-`ADMIN` role correctly `403`ing. New `admin1` (`ADMIN`) test user
created for this.

**Done in a prior session (Phase 8)** — run against the live `stjohns`
hospital's existing Paracetamol drug/stock fixture (from the Phase 5
verification): `GET /api/stock/dashboard` and `GET /api/drugs/3/stock`
correctly read the pre-existing 40-unit `SPRINGVERIFY-001` batch and its
prior dispense transaction; `POST .../stock/receive` with a brand-new batch
number created a second `Stock` row (`PHASE8-BATCH`, 30 units) and an `IN`
transaction, while repeating it against the existing batch number topped up
its quantity (40→45) in place instead of duplicating the row, both
confirmed by re-reading the drug detail endpoint; a past expiry date
correctly `400`s before touching anything; `POST .../stock/adjust`
correctly deducted a write-off (30→25) and recorded an `OUT` transaction,
correctly `409`s when asked to remove more than a batch holds, and a
non-stock-manager role hitting the dashboard correctly `403`s. New
`stockmgr1` (`STOCK_MANAGER`) test user created for this.

**Done in a prior session (Phase 7)** — a fresh `Patient`/`Appointment`/`Visit`
(id 4) registered → checked in → `WAITING_DOCTOR`, then run through the live
Nurse API against the real `stjohns` hospital: `GET /api/nurse/queue` listed
it with `hasVitals: false`, `POST /api/visits/4/nurse-vitals` recorded vitals
and flipped the queue's `hasVitals` to `true`, a doctor starting the
consultation moved the visit to `IN_CONSULTATION` and correctly dropped it
from the nurse queue, a further nurse-vitals call against that same visit
correctly `409`s (past triage), and a non-nurse role hitting the queue
correctly `403`s. New `nurse1` (`NURSE`) test user created for this; the
`doctor1`/`reception1` passwords from the prior session's fixtures had been
lost, so both were reset to new known values as part of this run (see fixture
list below — real values live only in local `manage.py shell` history, not
committed anywhere).

**Done in a prior session (Phases 1→6)** — full Phase 1→6 flow run against the real
`stjohns` hospital in the live `HMS` database: `doctor1`, `reception1`,
`lab1`, `pharm1`, and a new `cashier1` test user; a new `Patient`/
`Appointment`/`Visit` all the way through registration → check-in →
consultation → lab order → recorded result → `WAITING_PHARMACY` →
dispensed → `COMPLETED` → **billed → partially paid → fully paid**, then
cross-checked by reading the same rows back from Django's own ORM
(`manage.py shell`) at every stage — genuine proof both apps share live
data, not just a schema. (One wrinkle hit during this cross-check: reading
`invoice.amount_paid`/`.balance_due` from a raw `manage.py shell` session
returned `0` at first — not a Spring bug, but the tenancy contextvar gotcha
CLAUDE.md already documents: those Django `@property`s call
`self.payments.all()` through the tenant-scoped default manager, which
needs `set_current_hospital(...)` called first in a script with no request
context. Once set, it matched the Spring API exactly.) Example commands:

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

curl -H "Host: <realsubdomain>.lvh.me:8000" -u nurse1:<pass> http://localhost:8080/api/nurse/queue
curl -H "Host: <realsubdomain>.lvh.me:8000" -u nurse1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/visits/<id>/nurse-vitals \
     -d '{"temperature":37.0,"pulseRate":72,"bloodPressure":"120/80","weight":70,"height":170}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u doctor1:<pass> -X POST http://localhost:8080/api/visits/<id>/start
curl -H "Host: <realsubdomain>.lvh.me:8000" -u doctor1:<pass> -X POST http://localhost:8080/api/visits/<id>/complete

curl -H "Host: <realsubdomain>.lvh.me:8000" -u lab1:<pass> http://localhost:8080/api/visits/<id>/lab-order
curl -H "Host: <realsubdomain>.lvh.me:8000" -u lab1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/visits/<id>/lab-order-items/<itemId>/result \
     -d '{"resultValue":"5.2","normalRange":"4.0-6.0","remarks":"Within normal range"}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u pharm1:<pass> http://localhost:8080/api/visits/<id>/prescription
curl -H "Host: <realsubdomain>.lvh.me:8000" -u pharm1:<pass> \
     -X POST http://localhost:8080/api/visits/<id>/prescription-items/<itemId>/dispense

curl -H "Host: <realsubdomain>.lvh.me:8000" -u cashier1:<pass> http://localhost:8080/api/visits/<id>/invoice
curl -H "Host: <realsubdomain>.lvh.me:8000" -u cashier1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/visits/<id>/invoice-items -d '{"serviceId":1,"quantity":2}'
curl -H "Host: <realsubdomain>.lvh.me:8000" -u cashier1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/visits/<id>/payments \
     -d '{"amountPaid":15.00,"method":"CASH","reference":"first"}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u stockmgr1:<pass> http://localhost:8080/api/stock/dashboard
curl -H "Host: <realsubdomain>.lvh.me:8000" -u stockmgr1:<pass> http://localhost:8080/api/drugs/<drugId>/stock
curl -H "Host: <realsubdomain>.lvh.me:8000" -u stockmgr1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/drugs/<drugId>/stock/receive \
     -d '{"batchNumber":"NEW-BATCH","quantity":30,"expiryDate":"2027-01-01"}'
curl -H "Host: <realsubdomain>.lvh.me:8000" -u stockmgr1:<pass> -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/drugs/<drugId>/stock/adjust \
     -d '{"batchId":<stockId>,"quantity":5,"reason":"Damaged in transit"}'

curl -H "Host: <realsubdomain>.lvh.me:8000" -u admin1:<pass> http://localhost:8080/api/admin/dashboard
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
verification, in case anyone needs to redo it: `doctor1`, `reception1`
(`RECEPTIONIST`), `lab1` (`LAB`), `pharm1` (`PHARMACIST`), `cashier1`
(`CASHIER`), `nurse1` (`NURSE`), `stockmgr1` (`STOCK_MANAGER`) and `admin1`
(`ADMIN`) test users (passwords reset to known values as needed across
sessions — `doctor1`/`reception1` for Phase 7), a `Drug` ("Paracetamol")
and `LabTest` ("CBC") row, a `Service` ("Consultation Fee", 20.00) row, a
`Patient`/`Appointment`/`Visit` (id 4, "Nurse Phase Test Patient") sitting
at `IN_CONSULTATION` from the Phase 7 run, and two `Stock` batches of
Paracetamol from Phases 5→8 combined (`SPRINGVERIFY-001`, 45 units after
Phase 8's top-up; `PHASE8-BATCH`, 25 units after its own write-off) — see
git history/session notes for the exact `manage.py shell` commands used,
not repeated here since they're throwaway local data, not part of the app.

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
- `CashierServiceTests` — invoice auto-creation on first view, invoice-item price copy + totals refresh, unknown-service rejection, payment-with-no-invoice-yet rejection, payment idempotency on an already-settled invoice, over-the-balance rejection, and receipt-number assignment + status advancement to `PAID`, all against mocked repositories.
- `NurseServiceTests` — triage-queue listing with the has-vitals flag, successful vitals recording on a `WAITING_DOCTOR` visit, rejection once a visit has moved past triage, and rejection for a nonexistent visit, all against mocked repositories.
- `StockManagerServiceTests` — dashboard aggregation (per-drug totals, expiry counts, recent transactions), per-drug batch status computation (`EXPIRED`/`EXPIRING_SOON`/`FRESH`), receive-stock creating a new batch vs. topping up an existing one, the future-expiry-date guard, adjust-stock's cross-drug ownership rejection, its insufficient-quantity rejection (no partial write), and a successful write-off, all against mocked repositories.
- `AdminServiceTests` — outstanding-balance arithmetic, the undefined (`null`) delta when yesterday's figure was zero, a computed percentage delta, low-stock-drug counting, grouped-row-to-`LabelCount` mapping, the 14-day zero-filled trend series, and recent-payment patient-name resolution through the invoice, all against mocked repositories.
- Audit logging is covered inline in each of `DoctorServiceTests`/`LabServiceTests`/`PharmacyServiceTests`/`CashierServiceTests` (not a separate `AuditServiceTests`, since `AuditService` itself has no branching logic to test beyond what mocking it verifies at each call site): one test per service asserting `auditService.record(...)` is called with the right action/table/record-id/user, plus one asserting it's *not* called on that service's idempotent early-return path.
- `TenantIsolationTests` — a real `@SpringBootTest`/`MockMvc` test against
  actual Postgres (not mocked repositories), see Phase 11 above for the
  full writeup: same username in two hospitals doesn't collide, login only
  authenticates against the correct hospital, a cross-hospital visit
  lookup is `404` not `403`/`409`, and a cross-hospital visit never
  surfaces in a tenant-scoped list endpoint.

## Not in scope for this phase

All 9 planned workflow phases (Reception, Doctor, Lab, Pharmacy, Cashier,
Nurse, Stock Manager, Admin) plus Phase 10 (AuditLog) and Phase 11
(TenantIsolationTests) are now done. Deliberately left out of Phase 9 and
still open, per the scope decisions made before starting it: bed/ward
occupancy and per-department doctor/nurse headcounts (needs `Ward`/`Bed`/
`Admission` and the `Doctor`/`Nurse` profile models — a whole
inpatient/staffing subsystem no phase has mapped), and
`grant_admin_staff_access` (Django-admin group access is meaningless here
— this app has no admin UI of its own). The separate patient-facing
portal/API (14 `@role_required("PATIENT")` views plus `hospital/api/`'s
JWT-based DRF API for the React Native app) remains a bigger,
architecturally different subsystem — own auth flow, messaging,
telemedicine, notifications — never part of this rebuild's phase list;
treat it as an open scope question, not an assumed future phase.

## Next

No more phases are queued. Candidates, in roughly ascending order of
effort: (1) `Ward`/`Bed`/`Admission` read-mapping to complete the Admin
dashboard's bed occupancy; (2) the patient-facing portal/API, which is
less "one more phase" and more a second rebuild effort with its own auth
model. Pick based on what's actually needed next rather than working this
list in order.
