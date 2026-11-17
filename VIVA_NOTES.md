# PS-117 — Viva Notes & Interview Guide
## Passport and Visa Appointment Slot Allocation Engine

---

## 1. High-Level Concept & Architectural Highlights

### What is PS-117?
PS-117 is a deterministic, rule-based backend engine for passport and consular appointment scheduling. It solves the critical government challenge of balancing strict statutory quotas, multi-tier citizen priorities, document fraud checks, police verification requirements, and dynamic operational events (cancellations, reschedules, no-shows, walk-ins).

### Key Design Patterns & Principles
- **Strategy & Comparator Pattern**: Priority queue ordering is encapsulated in a deterministic comparator that evaluates category rank, reschedule penalties, and submission timestamps.
- **Factory & Repository Pattern**: Input data parsing decouples raw CSV parsing from domain entity validation.
- **State Pattern / Finite State Machine**: Applications and appointments transition through strictly defined lifecycle states (`SUBMITTED` $\rightarrow$ `VALIDATED` / `DOC_BLOCK` $\rightarrow$ `ALLOCATED` $\rightarrow$ `COMPLETED` / `CANCELLED` / `EXPIRED` / `NO_SHOW` / `REJECTED`).
- **Separation of Concerns**: Document validation, quota management, slot allocation, lifecycle events, and billing are isolated in distinct packages.
- **Financial Precision**: All monetary values use `java.math.BigDecimal` with explicit scale (2 decimal places) and `RoundingMode.HALF_UP`. Floating-point types (`float`, `double`) are strictly prohibited in financial logic.

---

## 2. Deep Dive: Quota-Pool Allocation Engine

### Why Multiple Quota Pools?
Centres must guarantee accessibility for critical demographics while reserving urgent slots:
1. **TATKAL (20%)**: Reserved for urgent emergency travel.
2. **SENIOR_CITIZEN & MINOR (15%)**: Reserved for vulnerable demographics needing expedited processing.
3. **PCC (10%)**: Reserved for Police Clearance Certificate applicants (employment/immigration).
4. **NORMAL (Remainder)**: Available for all standard applicants.

### How Does the Mathematical Formula Work?
$$\text{Tatkal} = \lfloor 0.20 \times C \rfloor$$
$$\text{SeniorMinor} = \lfloor 0.15 \times C \rfloor$$
$$\text{PCC} = \lfloor 0.10 \times C \rfloor$$
$$\text{Normal} = C - (\text{Tatkal} + \text{SeniorMinor} + \text{PCC})$$

*Why remainder for Normal?*
Because integer rounding can create rounding drift. If a centre has capacity 28:
- Tatkal: $\lfloor 0.20 \times 28 \rfloor = 5$
- Senior/Minor: $\lfloor 0.15 \times 28 \rfloor = 4$
- PCC: $\lfloor 0.10 \times 28 \rfloor = 2$
- Normal: $28 - (5 + 4 + 2) = 17$
- **Total Sum**: $5 + 4 + 2 + 17 = 28$ (Strictly 100% capacity conservation, zero leakage).

### Quota Release Scheduler Mechanics
- **The Rule**: At 17:00 two working days before the appointment ($T-2$ days), unbooked capacity in `SENIOR_MINOR` and `PCC` is released into a common `RELEASED` pool.
- **The Invariant**: **TATKAL quota is NEVER released**. Emergency slots must remain available for genuine last-minute urgent applicants.
- **Consumption Hierarchy**:
  1. An applicant first attempts to consume their designated category pool (e.g., Senior consumes `SENIOR_MINOR`).
  2. If the category pool is exhausted, the engine falls back to `RELEASED` quota.
  3. If `RELEASED` is exhausted, allocation is denied or queued for overflow.

---

## 3. Deep Dive: Document Validation & Scoring Engine

### Mandatory Checklist Matrix
Validation requires checking service-specific requirements:
- **FRESH_PASSPORT**: Must have Address Proof, DOB Proof, and Photo.
- **RE_ISSUE**: Must have Old Passport and Photo. (Address proof required only if address changed).
- **TATKAL**: In addition to service proofs, must have Aadhaar in **`VERIFIED`** status. Missing or unverified Aadhaar causes immediate rejection.
- **MINOR**: Must have DOB Proof, Photo, and Parent Consent (`PARENT_PASSPORT` or `ANNEXURE_D`).
- **PCC**: Must have Passport Copy and Address Proof.

### Point Scoring Weights
- **VERIFIED**: $+2$ points (e.g., DigiLocker/UIDAI verified).
- **SELF_ATTESTED**: $+1$ point (physical photocopy signed by applicant).
- **UNVERIFIED**: $0$ points.

### Police Verification (PV) Mode Mapping
The aggregate document score determines the required police verification mode:
$$\text{Score} \ge 6 \implies \mathbf{POST} \quad \text{(Passport printed first; verification follows)}$$
$$3 \le \text{Score} \le 5 \implies \mathbf{PRE} \quad \text{(Police verification mandatory prior to printing)}$$
$$\text{Score} < 3 \implies \mathbf{DOC\_BLOCK} \quad \text{(Document Block; allocation halted)}$$

### Critical Exemption: RE_ISSUE with No Address Change
For renewal/re-issue applications where the residential address remains identical to the existing passport record, the system assigns **`NOT_REQUIRED`**, bypassing police verification regardless of score.

### Expiry Date Boundary
A document is valid through the end of its expiration day (`valid_until`). It is expired **only if** `valid_until.isBefore(appointmentDate)`.
- If a document expires on `2026-11-20` and the appointment is on `2026-11-20`, the document is **VALID** (Edge Case 4).

---

## 4. Deep Dive: Priority Queue Ordering

### Priority Tier Hierarchy
1. **Rank 1**: `TATKAL`
2. **Rank 2**: `SENIOR_CITIZEN` ($\text{Age} \ge 60$ on appointment day)
3. **Rank 3**: `MINOR` ($\text{Age} < 18$ on appointment day)
4. **Rank 4**: `DIFFERENTLY_ABLED`
5. **Rank 5**: `NORMAL`

### Age Evaluation Date
Age is evaluated as of the **appointment date**, NOT the application submission date:
- An applicant turning 60 on the appointment day qualifies as `SENIOR_CITIZEN` (Rank 2 priority + 10% fee discount).
- A minor turning 15 before the appointment day is treated as an adult for passport booklet fee calculation (₹1,500 vs ₹1,000).

### Tie-Breaking & Penalties
- **FIFO Ordering**: Within the same priority tier, applicants are ordered by `applicationDate` (earliest first), with `applicationId` as secondary tie-breaker.
- **Third Reschedule Demotion**: If an applicant requests a 3rd reschedule, they are moved to the back of their priority tier (`thirdRescheduleMovedToBack = true`).

---

## 5. Lifecycle Events, Billing & Overbooking

### Cancellation Refund Schedule
Calculated from the time delta $\Delta T = \text{AppointmentTime} - \text{CancellationTime}$:
- **$\Delta T > 48$ Hours**: $\text{Refund} = \text{Base Fee} - ₹100$ (Administrative fee).
- **$24 \text{ Hours} \le \Delta T \le 48 \text{ Hours}$**: $\text{Refund} = 50\% \times \text{Base Fee}$ (Cancellation at exactly 48h is 50%).
- **$\Delta T < 24$ Hours**: ₹0 (No refund).
- **Tatkal Surcharge**: ₹2,000 surcharge is strictly **NON-REFUNDABLE** in all scenarios.

### Rescheduling Rules
- Maximum **3** reschedules allowed. 4th attempt is rejected.
- Spacing must be **$\ge 7$ days** from the previous appointment. Exactly 7 days is permitted.

### No-Show Penalties
- **1st No-Show**: Appointment marked `EXPIRED` / `NO_SHOW`. No refund.
- **2nd No-Show**: Application status set to `REJECTED`, `eligibleForAllocation = false`. Permanent block within the cycle.

### Walk-In Rules
- Reserved strictly for `SENIOR_CITIZEN` and `DIFFERENTLY_ABLED`.
- Daily walk-in cap $= \lfloor \text{Daily Capacity} \times 0.10 \rfloor$. For capacity 35, cap is 3.

### Slot Overbooking & WAIT Flag
- Overbooking capacity per slot $= \lfloor \text{Slot Capacity} \times 0.10 \rfloor$.
- For slot capacity 7, overbooking is 0.
- When an applicant is allocated within the overbooking allowance, their appointment status is flagged as **`WAIT`**.

---

## 6. Top Viva Questions & Model Answers

### Q1: Why did you choose `BigDecimal` over `double` for fee and refund calculations?
> **Answer**: In Java, binary floating-point types (`double`, `float`) represent fractional numbers as powers of 2, which causes precision loss (e.g., `0.1 + 0.2 = 0.30000000000000004`). In financial systems, statutory fee deductions and percentage refunds must be exact to the paisa. Using `BigDecimal` with explicit scale (2 decimal places) and `RoundingMode.HALF_UP` prevents financial discrepancies and ensures statutory compliance.

### Q2: Why is age evaluated on the appointment date rather than the application date?
> **Answer**: Legal rights and physical processing occur at the centre on the appointment date. If an applicant turns 60 on the day of their appointment, they legally appear at the counter as a senior citizen, entitled to senior citizen priority assistance and the statutory fee concession.

### Q3: Why is Tatkal quota never released into the released quota pool?
> **Answer**: Tatkal slots are reserved for urgent, emergency passport applicants who often require bookings on very short notice (1-3 days). If Tatkal slots were released into the general pool at $T-2$ days, emergency travelers would be locked out, defeating the purpose of the Tatkal scheme.

### Q4: How does overbooking behave for small slot capacities (e.g., 7)?
> **Answer**: The formula is $\lfloor \text{Slot Capacity} \times 0.10 \rfloor$. For a slot capacity of 7, $7 \times 0.10 = 0.7$, which truncates down to 0. Therefore, no overbooking is permitted for that slot. This prevents micro-slots from being overbooked beyond physical counter capacity.

### Q5: How do you guarantee deterministic results when executing CLI commands?
> **Answer**: We enforce determinism through three mechanisms:
> 1. Explicit reference time passed via `--as-of <timestamp>`, eliminating system clock dependence.
> 2. Stable sorting algorithms with deterministic tie-breakers (Category $\rightarrow$ Reschedule Flag $\rightarrow$ Application Timestamp $\rightarrow$ Application ID).
> 3. Strict transactional processing order for lifecycle events.
