# PS-117 — Decision Log
## Passport and Visa Appointment Slot Allocation Engine

---

### DEC-001: Priority Category Age Evaluation on Appointment Date
- **Context**: An applicant's age can change between the date of application submission and the appointment date.
- **Decision**: Age is strictly evaluated as of the **appointment date** (`Period.between(dob, appointmentDate).getYears()`).
- **Rationale**: An applicant turning 60 on the appointment date is legally a senior citizen when presenting at the centre and entitled to senior citizen priority and fee concessions. Similarly, a minor turning 15 before the appointment is legally an adult for passport booklet and fee purposes on the appointment date.
- **Reference**: BR-02, EC-01, EC-02.

---

### DEC-002: Integer Truncation for Quota Allocation and Remainder Assignment
- **Context**: Quota percentages (TATKAL 20%, SENIOR_MINOR 15%, PCC 10%) can produce fractional slot counts depending on centre capacity.
- **Decision**: Each special pool uses integer truncation (`Math.floor`):
  - $\text{TATKAL} = \lfloor 0.20 \times C \rfloor$
  - $\text{SENIOR\_MINOR} = \lfloor 0.15 \times C \rfloor$
  - $\text{PCC} = \lfloor 0.10 \times C \rfloor$
  - $\text{NORMAL} = C - (\text{TATKAL} + \text{SENIOR\_MINOR} + \text{PCC})$
- **Rationale**: Guarantees zero slot leakage or fractional slot rounding errors. The sum of all pools strictly equals total centre capacity $C$ across all capacity sizes.
- **Reference**: BR-01.

---

### DEC-003: Quota Release Idempotence and Tatkal Quota Protection
- **Context**: Quotas need to be released for unallocated capacity 2 days prior to appointment date at 17:00.
- **Decision**: Non-TATKAL pools (`SENIOR_MINOR`, `PCC`) release unallocated capacity into a dedicated `RELEASED` pool. The release action decrements the source pool remaining capacity and increments released capacity. Tatkal quota is explicitly non-releasable (`release()` throws `IllegalStateException` or returns 0).
- **Rationale**: Protects Tatkal urgent availability while preventing unutilized senior/minor/PCC slots from going waste. Idempotent check ensures running the release job repeatedly does not duplicate capacity.
- **Reference**: BR-01, EC-03.

---

### DEC-004: Document Expiration Date Boundary Invariant
- **Context**: Ambiguity regarding whether a document expiring on the appointment date itself is valid or expired.
- **Decision**: Documents are considered valid up to and including the date in `valid_until`. Expiration is evaluated as `doc.getValidUntil().isBefore(appointmentDate)`.
- **Rationale**: Legal identity documents are valid through the entirety of the expiration day. Therefore, a document expiring on `2026-11-20` is valid for an appointment on `2026-11-20` (EC-04) and only expired on `2026-11-21`.
- **Reference**: BR-03, EC-04.

---

### DEC-005: 48-Hour Cancellation Boundary and Surcharge Exclusion
- **Context**: Establishing precise tier thresholds for cancellation refunds and surcharge treatment.
- **Decision**:
  - Duration $\Delta T > 48$ hours: Full base fee refund less ₹100 administrative charge.
  - Duration $24 \le \Delta T \le 48$ hours: Exactly 50% refund of base service fee.
  - Duration $\Delta T < 24$ hours: 0% refund.
  - Tatkal surcharge (₹2,000) is **100% non-refundable** under all circumstances.
- **Rationale**: Aligns with official rules where 48 hours is the upper bound of the 50% tier. Statutory surcharges for urgent handling are non-refundable once booked.
- **Reference**: BR-05, BR-09, EC-07.

---

### DEC-006: Reschedule Minimum Spacing and Third Reschedule Penalty
- **Context**: Frequency of allowable reschedules and penalty mechanics.
- **Decision**: Maximum allowable reschedules is capped at 3. Reschedule requests must be spaced at least 7 days after the previous booking/reschedule timestamp (`!requestedDate.isBefore(previousDate.plusDays(7))`). The first request is measured from the initial appointment booking timestamp; later requests are measured from the previous successful reschedule. On the 3rd reschedule, the applicant's record is flagged with `thirdRescheduleMovedToBack = true` and sorted behind normal applicants in the priority queue.
- **Rationale**: Prevents calendar hoarding. Reschedule on exactly day 7 is valid (EC-05). Fourth reschedule is rejected. The stream engine seeds the spacing check from `Appointment.bookedAt`.
- **Reference**: BR-06, EC-05.

---

### DEC-007: Two No-Show Lifecycle Enforcement
- **Context**: Penalizing repeated applicant absences.
- **Decision**:
  - 1st no-show: Appointment status transitions to `EXPIRED` / `NO_SHOW`. No refund.
  - 2nd no-show: Application status transitions to `ApplicationStatus.REJECTED` and `eligibleForAllocation = false`. All future bookings are permanently blocked in the cycle.
- **Rationale**: Ensures centre capacity is not wasted by repeat absentee applicants.
- **Reference**: BR-07.

---

### DEC-008: Walk-In Allocation Quota and Demographics
- **Context**: Walk-in quota sizing and demographic qualification.
- **Decision**: Only `SENIOR_CITIZEN` ($\ge 60$) and `DIFFERENTLY_ABLED` applicants may be accepted as walk-ins. Daily walk-in cap is $\lfloor \text{Daily Capacity} \times 0.10 \rfloor$.
- **Rationale**: Protects operational flow while providing emergency accessibility to vulnerable populations. For capacity 35, $\lfloor 35 \times 0.10 \rfloor = 3$ walk-ins max (EC-08).
- **Reference**: BR-08, EC-08.

---

### DEC-009: 10% Slot Overbooking and WAIT Status Assignment
- **Context**: Managing overbooking capacity and applicant notification.
- **Decision**: Overbooking buffer is $\lfloor \text{Slot Capacity} \times 0.10 \rfloor$. When slot capacity is 7, $\lfloor 7 \times 0.10 \rfloor = 0$, so no overbooking is permitted (EC-06). Any appointment allocated beyond base slot capacity is explicitly flagged with `AppointmentStatus.WAIT`.
- **Rationale**: Overbooked applicants must be clearly identified on day sheets and appointment letters as standby/waitlisted.
- **Reference**: BR-10, EC-06.

---

### DEC-010: Police Verification Exemption for RE_ISSUE
- **Context**: Police verification mode determination for passport renewals.
- **Decision**: Applications for `RE_ISSUE` where `addressChange == false` are assigned `VerificationMode.NOT_REQUIRED`, bypassing standard document point thresholds.
- **Rationale**: Aligns with official passport issuance manual where identity and address have already been verified in the primary passport database.
- **Reference**: BR-11.

---

### DEC-011: Independent CLI Command Dispatching
- **Context**: The assignment specification requires that `PROCESS`, `VALIDATE`, `ALLOCATE`, `QUOTA-REPORT`, `FEE-REPORT`, `DAY-SHEET`, and `EVENT-PROCESS` execute distinct, command-specific workflows rather than silently running a monolithic pipeline.
- **Decision**: `PassportSlotEngine` implements dedicated methods for each command, only loading relevant datasets, running command-specific logic, and emitting command-specific artifacts and summaries.
- **Rationale**: Complies with CLI requirements and prevents unnecessary side-effects or extraneous files during focused commands.
- **Reference**: Section F (CLI Requirements).


---

### DEC-012: TATKAL Category Preservation for Unverified Aadhaar Applications (APN-0091)
- **Context**: APN-0091 is a TATKAL applicant who did not supply a verified Aadhaar (`aadhaar_verified = false`). Previous implementation reverted its category to `NORMAL` in `validation.csv` because it failed the Aadhaar requirement.
- **Decision**: The applicant's declared priority category remains `TATKAL` in `validation.csv` (reflecting their application submission), while the document validation check blocks allocation with `status = DOC_BLOCK` and `blocking_gaps = "TATKAL requires VERIFIED Aadhaar"`.
- **Rationale**: An applicant applying under the TATKAL scheme does not become a general NORMAL applicant merely because a mandatory Tatkal document was missing; rather, they are a TATKAL applicant whose application is blocked due to a missing requirement.
- **Reference**: BR-02, BR-03, APN-0091.

---

### DEC-013: Verification Mode Domain Constraints in Evaluation CSV (APN-0098)
- **Context**: BR-11 assigns scores below 3 to `DOC_BLOCK`, while the sample-data README binding choice restricts the `verification_mode` CSV column to `POST`, `PRE`, or `SKIPPED`. These requirements conflict for APN-0098 (score 2). Separately, a missing mandatory document can block an applicant whose score still selects a valid verification mode.
- **Decision**: Keep validation status and the police-verification track semantically distinct. Internally, score below 3 is represented as `DOC_BLOCK` for document validation and allocation blocking. In the evaluation CSV, `DOC_BLOCK` is mapped to `PRE` in the verification column because the binding output enum admits only `POST`, `PRE`, or `SKIPPED`. The `status` column remains `DOC_BLOCK`.
- **Rationale**: The mapping occurs only at serialization to satisfy the required CSV enum; internal validation and allocation continue to see `DOC_BLOCK`.
- **Unresolved conflict**: BR-11's score `< 3` result is `DOC_BLOCK`, while the sample binding enum for the CSV verification column does not permit that value. The CSV's `PRE` value is a compatibility mapping, not a claim that the blocked application actually qualifies for PRE verification.
- **Reference**: BR-11, Sample Data README Binding Choices, APN-0098, APN-0111.

---

### DEC-014: APN-0055 Minor Category vs Under-15 Concession Fee Dual Boundary
- **Context**: APN-0055 was born on `2011-10-10`, applied on `2026-10-01` (aged 14), and had an appointment on `2026-10-14` (having turned 15 on `2026-10-10`).
- **Decision**:
  - **Priority category**: Evaluate independently under BR-02's age `< 18` definition. At age 15, APN-0055 remains `MINOR` and keeps the existing priority rank; do not alter the assignment's priority ordering.
  - **Fee**: Evaluate the under-15 concession independently. At age 15, APN-0055 no longer qualifies for the ₹1,000 concession and pays the standard 36-page fee of ₹1,500.00.
- **Conflicting sample wording**: EC-02 in `T055-PS-117-sample-data/README.md` says: *"MINOR under 15 (Rs 1000.00) at application, 15 (Rs 1500.00) on the appointment day"*. This explicitly distinguishes the age-15 fee (`Rs 1500.00`) but does not explicitly say that a 15-year-old ceases to be a `MINOR` priority category. It therefore does not override BR-02's under-18 category rule.
- **Rationale**: Category and fee eligibility have distinct age boundaries. The test verifies both outcomes: `MINOR` category at age 15 and the normal fee of ₹1,500.00.
- **Reference**: BR-02, BR-05, EC-02, APN-0055.

---

### DEC-015: APN-0016 Sample Reschedule Timing Conflict
- **Context**: The stream requests reschedules for APN-0016 at 2026-10-04 06:00, 2026-10-06 11:00 and 2026-10-13 11:00. Its initial appointment was booked on 2026-09-28 18:00.
- **Decision**: Enforce BR-06 uniformly: the first request must be at least seven days after the initial booking; subsequent requests must be at least seven days after the previous successful reschedule. Do not waive this rule for a particular applicant or edit the supplied command stream.
- **Observed result**: The 2026-10-04 request is rejected because it is less than seven days after booking. The 2026-10-06 request is accepted; the 2026-10-13 request is accepted exactly seven days later. The stream therefore has two successful reschedules, not three.
- **Conflict**: The sample-data README describes these as two preceding reschedules followed by a third on the seven-day boundary, but the first two supplied request timestamps are only two days apart. The isolated EC-05 unit test covers a third reschedule at the exact seven-day boundary using a seeded prior reschedule; it does not make the inconsistent stream commands all succeed.
- **Reference**: BR-06, EC-05, `commands.txt` lines 27, 41 and 116.
