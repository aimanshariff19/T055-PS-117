# T055-PS-117 Design Document

## Introduction and problem statement

T055-PS-117 is a Java command-line simulation for passport and visa appointment allocation. It reads application, centre, document, holiday and capacity data, then processes an ordered command stream covering allocations and appointment lifecycle events.

The problem is to allocate limited appointment capacity fairly while applying document, priority, quota and financial rules consistently and retaining auditable CSV output.

## Objectives and scope

- Validate application documents and calculate document scores.
- Rank eligible applications and allocate available slots against quota pools.
- Process cancellations, reschedules, no-shows, rebookings and walk-ins.
- Calculate fees, concessions, refunds and forfeitures.
- Produce CSV reports and a readable terminal summary.

The stream engine is a deterministic in-memory simulation; it is not a web service and does not use a database.

## Functional requirements

- Read UTF-8 CSV inputs and pipe-delimited commands, ignoring blank and comment lines.
- Process the 8 supplied command types in physical file order.
- Maintain appointment, quota and simulation state across commands.
- Record rejected/refused commands with reasons.
- Generate validation, appointment, fee, day-sheet and command-log outputs.

## Non-functional requirements

- Java 25 and Maven build.
- Repeatable calculations using explicit simulation timestamps and `BigDecimal` for monetary values.
- CSV files are rewritten on each execution so prior report rows do not persist.
- Keep processing, business rules and CSV handling in distinct packages.

## Technology and architecture

- Java 25, Maven, Apache Commons CSV, JUnit 5.
- `Main` parses CLI modes and launches the relevant engine.
- `CommandStreamEngine` loads stream inputs, owns simulation state, dispatches each command and writes the stream reports.
- `PassportSlotEngine` supports the separate batch CLI pipeline.
- `allocation`, `validation`, `billing` and `lifecycle` packages implement business rules.
- `domain` and `enums` hold data models and statuses; `infrastructure` reads and writes CSV; `reporting` produces batch reports.

## Input data

The stream inputs are in `T055-PS-117-sample-data\`:

| File | Headers | Records / commands |
|---|---|---:|
| `applications.csv` | `app_id, applicant_name, gender, dob, mobile, service_type, booklet_pages, centre_id, preferred_date, preferred_band, application_ts, channel, differently_abled, aadhaar_verified, address_changed, old_passport_expiry, lost_or_damaged` | 120 |
| `documents.csv` | `doc_id, app_id, doc_type, verification, valid_until` | 339 |
| `centres.csv` | `centre_id, centre_name, city, slot_minutes, first_slot, last_slot` | 3 |
| `holidays.csv` | `holiday_date, holiday_name` | 3 |
| `slot_capacity.csv` | `centre_id, slot_date, slot_time, capacity` | 840 |
| `doc_checklist.csv` | `service_type, applies_when, requirement, accepted_doc_types, min_count` | 11 |
| `fee_table.csv` | `fee_code, amount, notes` | 10 |
| `commands.txt` | `COMMAND|arg1|arg2|...` | 266 commands |

Dates use `YYYY-MM-DD`; timestamps use `YYYY-MM-DD HH:MM:SS`; booleans use `true`/`false`. Application/document and application/centre relationships are keyed by `app_id` and `centre_id`. The separate `sample-data\` directory supports the batch CLI and its tests; it is not a substitute for the stream pack.

## Processing workflow

1. Validate required stream input paths.
2. Load reference and application data and validate documents.
3. Parse executable commands, ignoring blanks and `#` comments.
4. Advance the simulation clock and process each command in file order.
5. Record outcomes, then rewrite all stream report files.
6. Print counts, totals, rejected/refused reasons and output location.

## Business rules

The implementation applies BR-01 through BR-12 as indexed in [BUSINESS_RULE_REGISTER.md](BUSINESS_RULE_REGISTER.md). The principal rules cover floor-rounded quota pools and release, priority by category and FIFO, mandatory document checks and scoring, working-day booking windows, fees, reschedule limits, no-show/rebook handling, walk-in limits, refunds, overbooking and stream ordering.

The sample data has a reschedule sequence conflict for APN-0016. The 2026-10-04 request is less than seven days after the original booking; that request is rejected. The 2026-10-06 and 2026-10-13 requests then succeed, making the last one the second successful reschedule, not the third. The isolated boundary unit test verifies a third request exactly seven days after a seeded second request. See DEC-015 in the decision log.

The `verification_mode` evaluation schema allows only `POST`, `PRE` and `SKIPPED`, while BR-11 uses `DOC_BLOCK` for a score below 3. The internal result remains `DOC_BLOCK`; CSV serialization maps that value to `PRE` in the verification column only. The validation status remains `DOC_BLOCK`.

## Output reports

- `validation.csv`: one validation result per application.
- `appointments.csv`: allocation and subsequent appointment actions.
- `fees.csv`: one fee/refund/forfeiture row per application.
- `day_sheets.csv`: records produced by `PRINT_DAY_SHEET` commands.
- `command-log.txt`: command outcomes and failure reasons.

The command stream writes the exact headers required by the supplied sample-data specification. CSV output uses UTF-8 and is truncated before rewrite.

## Testing

Run `& .\mvnw.cmd clean package` for a clean Java 25 build and complete JUnit suite. The suite covers allocation, fees, validation, CLI parsing and documented business-rule edges. The full command stream is an additional integration check; its observed counts and unresolved data conflicts are recorded in [FINAL_AUDIT_REPORT.md](../../FINAL_AUDIT_REPORT.md).

## Limitations

- Runtime state is in memory; no persistence is provided between runs.
- The stream uses the actual data pack, which includes conflicting APN-0016 reschedule timing.
- The `DOC_BLOCK`/verification-mode output enum conflict is handled at CSV serialization as described above.
- The repository retains both a stream engine and a separate batch engine for compatibility with the existing CLI.

## Conclusion

The project is a Java 25 CLI simulation with separate rule services, CSV inputs/outputs and a test suite. Its two operating modes are retained; the documented single command runs the full sample command stream.
