# PS-117 – Passport and Visa Appointment Slot Allocation Engine with Priority Categories and Document Checks — Sample Data Pack

Prepared by 10 SECONDS for Team T055.

## Files

| File | What it holds | Row count |
|---|---|---|
| `centres.csv` | 3 Passport Seva Kendras with slot length and hours | 3 data rows |
| `holidays.csv` | Holidays for working-day windows | 3 data rows |
| `slot_capacity.csv` | Capacity of every 15-minute slot, 3 centres x 10 working days x 28 slots | 840 data rows |
| `doc_checklist.csv` | Document checklist matrix by service type | 11 data rows |
| `fee_table.csv` | Fee amounts from the brief | 10 data rows |
| `applications.csv` | 120 applications with service, category flags, preference and channel | 120 data rows |
| `documents.csv` | Uploaded documents per application with verification status and validity | 339 data rows |
| `commands.txt` | Allocation runs, ~80 reschedule/cancel/no-show/rebook/walk-in events, appointment letters and day sheets | 266 command lines (excluding comments) |

Row numbers quoted below are 1-based data-row numbers (the header is row 0). Line numbers for `commands.txt` are physical line numbers including comment lines.

## Column reference

Conventions: dates `YYYY-MM-DD`; timestamps `YYYY-MM-DD HH:MM:SS` (24h, IST, no timezone suffix); money in Rs as plain decimals with 2 places (no commas, no symbol); percentages as plain numbers (12.5 means 12.5%); booleans `true`/`false`; IDs uppercase with a fixed prefix. An empty cell means "not applicable" and is only allowed where the notes say so.

### `centres.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| centre_id | string | PSK-nn | — | — |
| centre_name | string | — | — | — |
| city | string | — | — | — |
| slot_minutes | int | min | 15 | — |
| first_slot | time | HH:MM | — | — |
| last_slot | time | HH:MM | — | start of the last slot |

### `holidays.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| holiday_date | date | — | — | — |
| holiday_name | string | — | — | — |

### `slot_capacity.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| centre_id | string | — | centres.csv | — |
| slot_date | date | — | — | working days only |
| slot_time | time | HH:MM | — | slot start |
| capacity | int | applicants | — | day capacity = sum over the day's slots |

### `doc_checklist.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| service_type | enum | — | NORMAL, TATKAL, RE-ISSUE, PCC, ANY | TATKAL uses the NORMAL list |
| applies_when | enum | — | ALWAYS, ADDRESS_CHANGED, APPLICANT_MINOR | — |
| requirement | string | — | — | — |
| accepted_doc_types | string | semicolon list | — | any one satisfies the requirement |
| min_count | int | — | 1 | — |

### `fee_table.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| fee_code | string | — | — | — |
| amount | decimal | Rs (or pct for SENIOR_DISCOUNT_PCT) | — | — |
| notes | string | — | — | may be empty |

### `applications.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| app_id | string | APN-nnnn | — | — |
| applicant_name | string | — | — | — |
| gender | enum | — | M, F | — |
| dob | date | — | — | age decides SENIOR (>= 60) / MINOR (< 18) |
| mobile | string | 10 digits | — | — |
| service_type | enum | — | NORMAL, TATKAL, RE-ISSUE, PCC | — |
| booklet_pages | int | — | 36, 60 | — |
| centre_id | string | — | centres.csv | — |
| preferred_date | date | — | — | earliest acceptable appointment date |
| preferred_band | enum | — | MORNING (09:00–11:45), AFTERNOON (12:00–15:45), ANY | slot time preference |
| application_ts | timestamp | — | — | priority tie-break |
| channel | enum | — | ONLINE, WALK_IN | WALK_IN applicants are not allocated; they arrive via WALK_IN |
| differently_abled | bool | — | — | — |
| aadhaar_verified | bool | — | — | TATKAL requires true |
| address_changed | bool | — | — | RE-ISSUE only |
| old_passport_expiry | date | — | — | RE-ISSUE only, else empty |
| lost_or_damaged | bool | — | — | — |

### `documents.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| doc_id | string | DOC-nnnnn | — | — |
| app_id | string | — | applications.csv | — |
| doc_type | string | — | see doc_checklist.csv plus PHOTO, OLD_PASSPORT, VALID_PASSPORT, PARENT_PASSPORT, ANNEXURE_D | — |
| verification | enum | — | VERIFIED (+2), SELF_ATTESTED (+1) | document score |
| valid_until | date | — | — | empty = no expiry; a date before the appointment date = expired |

## Command file format

`commands.txt` holds one command per line in the form `COMMAND_NAME|arg1|arg2|...`. Commands must be processed strictly in file order. Lines starting with `#` are comments and blank lines are ignored. An argument written as `-` means "not applicable". Every command used in this pack:

| Command | Arguments (in order) | Meaning |
|---|---|---|
| `RUN_ALLOCATION` | `run_ts` | Allocate all pending, document-valid applications with application_ts <= run_ts, by priority |
| `RESCHEDULE` | `app_id|request_ts|new_preferred_date|band` | Move an active appointment |
| `CANCEL` | `app_id|request_ts` | Cancel with refund per the cancellation bands |
| `NO_SHOW` | `app_id|appointment_date` | Applicant did not turn up — appointment EXPIRED |
| `REBOOK` | `app_id|request_ts|preferred_date|band` | Rebook after a no-show (free once within 30 days) |
| `WALK_IN` | `app_id|centre_id|arrival_ts` | Walk-in (SENIOR_CITIZEN / DIFFERENTLY_ABLED) into a vacant slot |
| `PRINT_APPLICATION` | `app_id` | Validation result, allocation, fee and refund |
| `PRINT_DAY_SHEET` | `centre_id|date` | — |

## Planted scenarios

Every edge case in the brief, mapped to the exact record where it occurs, followed by extra traps.

- **EC-01 Applicant turning 60 on the appointment day**
  - APN-0099: dob 1966-10-13, preferred appointment 2026-10-13 — turns 60 ON the appointment day: NORMAL at application time, SENIOR_CITIZEN on the day (priority, 10% base-fee discount) — `applications.csv row 99`
- **EC-02 Minor turning 15 between application and appointment**
  - APN-0055: dob 2011-10-10, applied 2026-10-01 aged 14, appointment on/after 2026-10-14 — turns 15 on 2026-10-10 in between: MINOR under 15 (Rs 1000.00) at application, 15 (Rs 1500.00) on the appointment day — `applications.csv row 55`
- **EC-03 Quota release at 17:00 two days before with TATKAL quota untouched**
  - PSK-02 on 2026-10-07 (day capacity 28): TATKAL quota 5 untouched by 17:00 on 2026-10-05; 17 NORMAL applicants already hold the 17 NORMAL seats; at 17:00 the unused TATKAL (and SENIOR/MINOR, PCC) quota is released, so APN-0087, APN-0088, APN-0089 (applied 17:20–17:30) get 2026-10-07 slots in the 18:00 run instead of the next day — `applications.csv row 87, applications.csv row 88, applications.csv row 89; run at 2026-10-05 18:00:00`
- **EC-04 Document expiring on the appointment date**
  - APN-0096 (PCC): ELECTRICITY_BILL valid_until 2026-10-15 = the appointment date — not yet 'past', so it is still valid (no DOC_BLOCK) — `documents.csv row 266`
- **EC-05 Third reschedule requested on day 7**
  - APN-0016: free reschedules on 2026-10-04 06:00:00 and 2026-10-06 11:00:00; the THIRD reschedule is requested on 2026-10-13 11:00:00 = exactly day 7 after the second — boundary of 'only after 7 days'; if allowed it goes to the back of its category queue — `commands.txt lines 27, 41, 116`
- **EC-06 Slot capacity 7 with 10% overbooking (rounds to 0)**
  - PSK-01 slot 2026-10-08 11:00 has capacity 7 — 10% overbooking = 0.7, rounded down to 0: no NORMAL overbooking in that slot — `slot_capacity.csv row 93`
- **EC-07 Cancellation at exactly 48 h**
  - APN-0044: appointment 2026-10-09 09:00:00 (60-page NORMAL, Rs 2000.00), CANCEL at 2026-10-07 09:00:00 = exactly 48 h before — falls in the 24–48 h band (50% refund), not the >48 h band — `commands.txt line 47`
- **EC-08 Walk-in cap of 10% when the day's capacity is 35**
  - PSK-03 on 2026-10-08: day capacity 35 (7 slots x 2 + 21 x 1). 10% of 35 = 3.5 -> walk-in cap 3 (rounded down); 5 booked applicants are NO_SHOW (vacant slots), 4 walk-ins arrive (3 seniors, 1 differently-abled) — the 4th must be turned away; walk-ins are served after scheduled applicants of that slot — `slot_capacity.csv PSK-03 2026-10-08; no-shows commands.txt lines 58, 59, 62, 63, 66; walk-ins commands.txt lines 67, 69, 71, 72`
- **Extra traps (not in the brief, but realistic):**
  - APN-0032: NO_SHOW, free REBOOK within 30 days, then a second NO_SHOW on 2026-10-12 — fee forfeited, a fresh application with a fresh fee is required — `commands.txt lines 37, 49, 104`
  - APN-0018: REBOOK while its appointment is still active — REJECTED (one active appointment per application) — `commands.txt line 36`
  - APN-0111: RENT_AGREEMENT expired the day before the appointment — counts as missing address proof -> DOC_BLOCK — `documents.csv row 309`
  - APN-0091: TATKAL applicant without a VERIFIED Aadhaar flag — not allocated — `applications.csv row 91`
  - APN-0098: only two SELF_ATTESTED documents (score 2) and no photo — DOC_BLOCK — `applications.csv row 98`
  - APN-0080: no DOB proof — mandatory document missing, DOC_BLOCK — `applications.csv row 80`
  - APN-0035: minor (age 12) without parent passport / Annexure D — DOC_BLOCK — `applications.csv row 35`
  - APN-0013: RE-ISSUE, old passport expired 2022-06-30 (more than 3 years) — Rs 3000.00; no address change so police verification is skipped — `applications.csv row 13`
  - APN-0042: RE-ISSUE within 1 year of expiry (2026-02-28) with address change — NORMAL fee, address proof required — `applications.csv row 42`
  - APN-0081: lost passport, 60 pages — Rs 3500.00 — `applications.csv row 81`
  - APN-0103: TATKAL applicant aged 67 — TATKAL priority; senior 10% off the base fee only, the Rs 2000.00 TATKAL surcharge is not discounted — `applications.csv row 103`

## What is NOT in this pack

Expected outputs are not provided for this set. Validate your engine with your own test suite against the rules in your brief (BR-01 … BR-nn). A separate hidden input set with the same schema will be used at final evaluation; outputs are compared field-for-field, so follow the column formats above exactly in your own output.

## Output format expected at evaluation

Produce these CSV files (header row, same conventions as above, rows sorted as stated). Where the brief is silent on a format detail the choice below is binding for evaluation.

### `validation.csv`

One row per application.

Columns, in order: `app_id`, `category`, `doc_score`, `verification_mode`, `status`, `blocking_gaps`

### `appointments.csv`

One row per allocation/reallocation, in time order.

Columns, in order: `app_id`, `action`, `action_ts`, `centre_id`, `slot_date`, `slot_time`, `quota_pool`, `priority_reason`

### `fees.csv`

One row per application.

Columns, in order: `app_id`, `base_fee`, `senior_discount`, `tatkal_surcharge`, `total_fee`, `refund`, `forfeited`

### `day_sheets.csv`

One row per applicant per PRINT_DAY_SHEET, ordered by slot then priority (walk-ins last in their slot).

Columns, in order: `centre_id`, `slot_date`, `slot_time`, `app_id`, `category`, `status`, `wait_flag`

Binding choices: quota seats per day = floor(pct x day capacity) for TATKAL (20%), SENIOR_CITIZEN+MINOR (15%) and PCC (10%); NORMAL gets the remainder. `status` one of ALLOCATED, DOC_BLOCK, REJECTED, RESCHEDULED, CANCELLED, EXPIRED, WALK_IN_SERVED, WALK_IN_REFUSED, FORFEITED; `quota_pool` TATKAL, SENIOR_MINOR, PCC, NORMAL or RELEASED; `verification_mode` POST, PRE or SKIPPED; `blocking_gaps` semicolon list of missing requirements (empty if none).
