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
- **Decision**: Maximum allowable reschedules is capped at 3. Reschedule requests must be spaced at least 7 days after the previous booking/reschedule date (`!requestedDate.isBefore(previousDate.plusDays(7))`). On the 3rd reschedule, the applicant's record is flagged with `thirdRescheduleMovedToBack = true` and sorted behind normal applicants in the priority queue.
- **Rationale**: Prevents calendar hoarding. Reschedule on exactly day 7 is valid (EC-05). Fourth reschedule is rejected.
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
