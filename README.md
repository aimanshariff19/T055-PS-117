# T055-PS-117

## Passport and Visa Appointment Slot Allocation Engine

## Overview
This Maven command-line project targets Java 17 and includes the PS-117 processing pipeline and synthetic CSV inputs. The command selectors are accepted by the CLI, but they currently all invoke the same processing pipeline.

## Requirements
- Java 17 or newer

## Build and test
```sh
./mvnw clean compile
./mvnw test
./mvnw package
```

## Run
```sh
./scripts/run.sh --centres sample-data/centres.csv \
  --applications sample-data/applications.csv \
  --documents sample-data/documents.csv \
  --events sample-data/events.csv \
  --output output/run
```

Use `./scripts/run.sh --help` to display CLI options. Commands currently accepted: `PROCESS`, `VALIDATE`, `ALLOCATE`, `QUOTA-REPORT`, `FEE-REPORT`, `DAY-SHEET`, and `EVENT-PROCESS`.

## Local operations dashboard

The vanilla HTML/CSS/JavaScript dashboard is served by a small Java built-in HTTP server. The server binds to loopback only at `127.0.0.1:8080`.

```sh
./mvnw clean compile
./mvnw test
./mvnw package
./scripts/dashboard.sh
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080). The default inputs are the synthetic CSVs in `sample-data/`. Use **Run Java processing** in the dashboard to invoke the existing validation/allocation engine. Its reports are written to a new, unique directory under `output/dashboard-runs/`; the dashboard does not overwrite prior CLI reports.

API endpoints: `GET /api/health`, `/api/dashboard/summary`, `/api/applications`, `/api/appointments`, `/api/allocation-results`, `/api/documents`, `/api/centres`, `/api/priorities`, `/api/billing`, `/api/reports`, and `/api/settings`; `GET /api/reports/{report-id}` downloads an available CSV; `POST /api/process` runs the Java engine against the fixed configured inputs. The process endpoint accepts only an optional ISO local date-time `asOf` value and does not accept paths or shell commands.

### Data limitations

- Application status, validation outcomes, appointments, and slot availability are only returned after the Java engine runs in the current server session.
- `centres.csv` supplies daily/slot capacities; available slots are shown only from Java-generated slots after a run.
- The engine has no payment ledger or persisted verification timestamps. Billing shows Java `FeeCalculator` estimates only, never collected amounts or successful payments.
- Quota, fee-breakdown, and refund CSVs currently contain placeholder rows in the engine. Their exports are labelled and are not presented as live quota/payment data.
- The dashboard uses no frontend framework and does not reimplement allocation or validation rules.

If port 8080 is occupied, run `./scripts/dashboard.sh --port 8081`, then visit `http://127.0.0.1:8081`. The server remains loopback-only.

## Project structure
- `src/main/java/com/ps117` production code
- `src/main/resources/application.properties` default application configuration
- `src/test/java/com/ps117` tests
- `dashboard` local HTML/CSS/JavaScript interface
- `sample-data` sample CSV files
- `output` generated reports
