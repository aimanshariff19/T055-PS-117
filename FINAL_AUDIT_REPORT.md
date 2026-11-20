# Final Audit Report

**Verified:** 2026-11-20
**Scope:** Java 17 CLI cleanup, packaged application verification, official sample command-stream execution, generated reports and business-rule edge cases.

## 1. Build and Tests

- **Build command:** `.\mvnw.cmd clean package`
- **Build result:** `BUILD SUCCESS`
- **JUnit tests:** 39 run, 0 failures, 0 errors, 0 skipped.
- **Production Java source files compiled:** 61
- **Test source files compiled:** 7
- **Java compiler release target:** 17
- **Executable JAR:** `target\T055-PS-117-1.0.0.jar`
- **Verified Java runtime:** 17.0.21
- **Runtime dependency:** Apache Commons CSV.
- **JUnit 5:** Test-scoped dependency.

The packaged application was successfully created and executed using Java 17.

## 2. Official Sample Input and Command Execution

The official assignment input pack is located in `T055-PS-117-sample-data\`.

| Input file | Records / commands |
|---|---:|
| `applications.csv` | 120 |
| `documents.csv` | 339 |
| `centres.csv` | 3 |
| `holidays.csv` | 3 |
| `slot_capacity.csv` | 840 |
| `doc_checklist.csv` | 11 |
| `fee_table.csv` | 10 |
| `commands.txt` | 266 executable commands |

The packaged JAR was executed against the official command stream on **20 November 2026**.

**Verified execution command:**

```powershell
java -jar .\target\T055-PS-117-1.0.0.jar STREAM `
    --commands ".\T055-PS-117-sample-data\commands.txt" `
    --centres ".\T055-PS-117-sample-data\centres.csv" `
    --applications ".\T055-PS-117-sample-data\applications.csv" `
    --documents ".\T055-PS-117-sample-data\documents.csv" `
    --holidays ".\T055-PS-117-sample-data\holidays.csv" `
    --slot-capacity ".\T055-PS-117-sample-data\slot_capacity.csv" `
    --output ".\output"
```

### Execution summary

| Metric | Verified result |
|---|---:|
| Applications loaded | 120 |
| Documents loaded | 339 |
| Commands processed | 266 |
| Successful commands | 232 |
| Rejected / refused / failed (combined) | 34 |
| Appointments allocated | 135 |
| Document-blocked applications | 17 |
| Cancellations | 13 |
| Successful reschedules | 2 |
| No-shows | 27 |
| Walk-ins served | 3 |
| Walk-ins refused | 1 |
| Total fees assessed | ₹181,850.00 |
| Total refunds issued | ₹10,400.00 |
| Applications forfeited | 6 |

The execution summary combines rejected, refused and failed commands into a single count of 34. It does not independently provide the breakdown of those categories. Therefore, no separate classification is claimed here.

The application processed the official command stream successfully and reported that all expected output files were generated.

No frontend or API server is used. The application operates as a Java command-line simulation.

## 3. Generated Reports

The official command-stream execution generated the following five files in the ignored runtime output directory `output\`.

| Generated report | Verified data records / entries |
|---|---:|
| `validation.csv` | 120 |
| `appointments.csv` | 195 |
| `fees.csv` | 120 |
| `day_sheets.csv` | 132 |
| `command-log.txt` | 266 |

All five expected files were present after execution. The four CSV row counts were checked using PowerShell `Import-Csv`, and the command log was inspected.

The following totals were reported by the application:

- 135 initial appointment allocations.
- 17 document-blocked applications.
- 13 cancellations.
- 2 successful reschedules.
- 27 no-shows.
- 3 walk-ins served.
- 1 walk-in refused.
- ₹181,850.00 in assessed fees.
- ₹10,400.00 in refunds.
- 6 applications forfeited.

The execution summary and generated report counts provide evidence that the official input stream was processed and the expected reports were produced.

Header correctness and duplicate-row checks from the earlier audit remain historical observations; they were not independently repeated as part of the November 20 verification.

## 4. Business Rule and Edge-Case Audit

The following application-specific scenarios and observations were documented during the earlier audit and are retained for traceability.

### 4.1 Rescheduling — APN-0016

- The first reschedule request was rejected because it occurred less than seven days after the initial booking.
- Two later requests succeeded.
- The latter successful request occurred exactly seven days after the previous successful request.
- The supplied command stream therefore does not produce a third successful reschedule.

This discrepancy is documented in `DECISION_LOG.md`, DEC-015.

### 4.2 Walk-in Document Validation — APN-0019

- Document validation is performed at walk-in processing time.
- Verification score: 5.
- Verification mode: `PRE`.
- Validation status: `VALID`.
- Final walk-in result: `WALK_IN_SERVED`.

### 4.3 Repeated No-Show — APN-0032

- The first no-show expires the appointment.
- The second no-show blocks the application.
- The applicable fee is marked as forfeited.

### 4.4 Cancellation Refund Boundary — APN-0044

- Cancellation occurs exactly 48 hours before the appointment.
- The refund is ₹1,000.00.
- This scenario verifies the 24–48-hour cancellation boundary.

### 4.5 Minor Applicant — APN-0055

- Applicant age on appointment date: 15.
- Applicant remains in the `MINOR` category.
- Normal 36-page passport fee: ₹1,500.00.

### 4.6 Released Quota — APN-0087 to APN-0089

- These applications were allocated using the `RELEASED` quota.
- The scenarios demonstrate the use of released quota capacity.

### 4.7 Tatkal Aadhaar Requirement — APN-0091

- The application does not have verified Aadhaar.
- The application remains `DOC_BLOCK`.
- The Tatkal category is not treated as overriding the document requirement.

### 4.8 Verification Serialization — APN-0098

- Verification score: 2.
- Application status: `DOC_BLOCK`.
- The verification column is serialized as `PRE` because the binding CSV enum does not include the internal `DOC_BLOCK` value.
- The validation status retains `DOC_BLOCK`.

This remains a documented schema limitation.

### 4.9 Senior Citizen Discount — APN-0099

- Applicant qualifies for the senior citizen category.
- Base fee: ₹1,500.00.
- Senior citizen discount: ₹135.00.
- The discount is calculated as 10% of the base fee.

### 4.10 Walk-In Capacity Limit

- Three walk-ins are served.
- The fourth walk-in is refused.
- This verifies the configured walk-in capacity limit for the scenario.

These scenario observations are retained from the existing audit. The November 20 packaged stream execution confirms the overall execution totals, but does not independently rerun every isolated edge-case assertion.

## 5. Repository Cleanup and Structure

The repository cleanup retained the files and directories required for the Java CLI application, tests, documentation and build process.

### Retained project components

- Java application source code under `src\main\java\`.
- Application resources under `src\main\resources\`.
- Automated tests under `src\test\java\`.
- Maven project configuration in `pom.xml`.
- Maven Wrapper files.
- Supporting scripts under `scripts\`.
- Design documentation under `docs\design\`.
- `README.md`.
- `DECISION_LOG.md`.
- `FINAL_AUDIT_REPORT.md`.
- Official assignment input pack under `T055-PS-117-sample-data\`.

### Removed or excluded components

- Legacy generated `sample-data\` folder and its files.
- Empty or unnecessary viva documentation directories.
- Duplicate root-level design documents and obsolete PDF documentation.
- Old generated output trees and duplicate batch outputs.

The official assignment input pack was retained and was not intentionally modified.

### Generated output handling

- The `output\` directory is ignored by Git.
- The command-stream execution generates the five expected reports in this directory.
- The stream execution replaces its owned output files on subsequent runs.
- Generated reports are runtime artifacts and are not intended to be committed as project source files.

### Legacy batch CLI

The Java batch CLI, tests and supporting scripts remain part of the project.

Batch CLI users must explicitly supply an input directory containing:

- `centres.csv`
- `applications.csv`
- `documents.csv`
- `events.csv`

The legacy batch input folder is not included in the repository.

The official command-stream pack is a separate input format and is not treated as a replacement for the legacy batch input directory.

## 6. Remaining Specification and Data Ambiguities

Two unresolved issues remain documented in `DECISION_LOG.md`.

### 6.1 APN-0016 — Reschedule Date Conflict

The supplied command dates conflict with the seven-day reschedule spacing requirement.

The first request is rejected, while the subsequent two requests succeed. The official sample therefore does not demonstrate three successful reschedules.

This is retained as a documented input/specification discrepancy rather than being silently treated as resolved.

### 6.2 DOC_BLOCK — Verification CSV Enum Conflict

The internal verification result supports `DOC_BLOCK`, but the binding CSV verification enum does not include this value.

The current serialization maps the verification field to `PRE` while retaining `DOC_BLOCK` in the validation status.

This is a documented schema limitation. The report does not claim that the underlying schema conflict has been fixed.

## 7. Final Verification Status

| Verification area | Result |
|---|---|
| Java 17 compatibility | Verified |
| Maven clean package | Successful |
| Automated test suite | 39 passed; no failures or errors |
| Executable JAR creation | Verified |
| JAR version check | Successful |
| Official assignment command-stream execution | Successful |
| Official input loading | 120 applications and 339 documents |
| Command processing | 266 commands processed |
| Expected output generation | All five files generated |
| CSV report row counts | Verified |
| Command log | Generated and inspected |
| Business-rule edge-case observations | Documented |
| APN-0016 specification discrepancy | Unresolved and documented |
| DOC_BLOCK CSV serialization limitation | Unresolved and documented |
| Official assignment input preservation | No changes reported |

### Final Assessment

The project successfully builds with Java 17, passes the verified automated test suite, produces the executable JAR and processes the official assignment command stream.

The packaged application generated all five expected reports and reported the execution totals documented in this audit.

The repository retains its Java CLI implementation, automated tests, supporting scripts, official assignment input pack and design documentation. Generated runtime reports are excluded from version control.

The APN-0016 rescheduling discrepancy and DOC_BLOCK verification serialization limitation remain explicitly documented and should be considered during final review.

**Overall conclusion:** The build and official command-stream execution are verified. The project has a documented set of remaining specification and serialization limitations that should be reviewed before final submission. This report does not claim that the repository has been committed, pushed, or independently cleared through a complete final Git diff review.