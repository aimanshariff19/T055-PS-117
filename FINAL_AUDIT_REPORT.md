# Final Audit Report

**Verified:** 2026-11-19  
**Scope:** Java 17 CLI cleanup, sample command-stream run, reports and business-rule edge cases.

## Build and tests

- Command: `& .\mvnw.cmd clean package`
- Result: `BUILD SUCCESS`
- JUnit: **37 run, 0 failures, 0 errors, 0 skipped**
- Main Java sources compiled: 62
- Executable: `target\T055-PS-117-1.0.0.jar`
- Runtime dependencies retained: Apache Commons CSV. JUnit 5 remains test-scoped.

## Sample input and command execution

The actual stream pack in `T055-PS-117-sample-data\` contains:

| File | Records / commands |
|---|---:|
| `applications.csv` | 120 |
| `documents.csv` | 339 |
| `centres.csv` | 3 |
| `holidays.csv` | 3 |
| `slot_capacity.csv` | 840 |
| `doc_checklist.csv` | 11 |
| `fee_table.csv` | 10 |
| `commands.txt` | 266 executable commands |

The run in `output\` recorded **266 outcomes**: 232 successful, 32 rejected, 1 refused, 1 blocked, 0 errors, 0 skipped. The command log includes one outcome per input command. No frontend or API server is used.

## Generated reports

Only these five files are present in the ignored runtime output directory:

| Report | Data records | Header verified | Duplicate rows |
|---|---:|:---:|---:|
| `validation.csv` | 120 | Yes | 0 |
| `appointments.csv` | 195 | Yes | 0 |
| `fees.csv` | 120 | Yes | 0 |
| `day_sheets.csv` | 132 | Yes | 0 |
| `command-log.txt` | 266 command outcomes | — | — |

Observed totals: 135 initial allocations, 17 document-blocked applications, 13 cancellations, 2 successful reschedules, 27 no-shows, 3 walk-ins served and 1 refused; assessed fees ₹181,850.00, refunds ₹10,400.00 and 6 forfeitures. The active appointment event sequence contains no duplicate active allocations.

## Scenario checks

- **APN-0016:** first request rejected for being less than seven days after initial booking; the later two requests succeed, with the latter exactly seven days after the previous successful request. The supplied stream therefore does not produce a third successful reschedule. See DEC-015.
- **APN-0019:** document validation now runs at walk-in time; score 5, `PRE`, `VALID`, and `WALK_IN_SERVED`.
- **APN-0032:** first no-show expires the appointment; second no-show blocks the application and marks the fee forfeited.
- **APN-0044:** cancellation exactly 48 hours before appointment refunds ₹1,000.00.
- **APN-0055:** age 15 on appointment date; normal 36-page fee ₹1,500.00.
- **APN-0087–0089:** allocated from `RELEASED` quota.
- **APN-0091:** Tatkal application remains `DOC_BLOCK` without verified Aadhaar.
- **APN-0098:** score 2 and status `DOC_BLOCK`; verification column is `PRE` only because the binding CSV enum excludes internal `DOC_BLOCK`.
- **APN-0099:** senior category and ₹135.00 discount on the ₹1,500.00 base fee.
- Walk-in cap scenario: 3 served, 4th refused.

## Cleanup and remaining ambiguity

Old output trees and duplicate batch outputs were removed; `output\` is ignored by Git and each stream run clears/replaces its five owned files. Root duplicate design and viva documents, the PDF, and viva directory were removed. The Java batch CLI, tests and supporting scripts remain; batch CLI users must now supply their input directory explicitly.

Two specification/data ambiguities remain documented in `DECISION_LOG.md`:

1. APN-0016's supplied request dates conflict with the seven-day reschedule rule.
2. The internal `DOC_BLOCK` verification result conflicts with the allowed CSV verification enum; serialization maps the verification field to `PRE` while retaining `DOC_BLOCK` in validation status.

No official stream-pack input was changed. The rule register, diagrams and design document are in `docs\design\`. The legacy batch input folder is not included; batch CLI users must supply an input directory containing `centres.csv`, `applications.csv`, `documents.csv`, and `events.csv`.
