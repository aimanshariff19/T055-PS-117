# T055-PS-117

Java 17 command-line simulation of passport appointment validation, priority allocation, quota handling, fees and appointment lifecycle events.

## Requirements

- JDK 17 or newer
- PowerShell on Windows
- Maven Wrapper included in this repository

## Build and test

From the repository root:

```powershell
& .\mvnw.cmd clean package
& .\mvnw.cmd test
```

## Run the sample command stream

The stream input files are in `T055-PS-117-sample-data\`. Run the following one-line command from the repository root after building:

```powershell
java -jar target\T055-PS-117-1.0.0.jar STREAM --commands "T055-PS-117-sample-data\commands.txt" --centres "T055-PS-117-sample-data\centres.csv" --applications "T055-PS-117-sample-data\applications.csv" --documents "T055-PS-117-sample-data\documents.csv" --holidays "T055-PS-117-sample-data\holidays.csv" --slot-capacity "T055-PS-117-sample-data\slot_capacity.csv" --output "output"
```

The stream processes commands in file order. It loads 120 applications, 339 documents, 3 centres, 3 holidays and 840 slot-capacity records; `commands.txt` contains 266 executable commands.

## Outputs

The command-stream run writes these reports to the selected output directory, replacing those files on each run:

- `validation.csv` — validation score, verification mode, status and blocking gaps.
- `appointments.csv` — successful allocations and reallocations, in chronological order.
- `fees.csv` — fees, concessions, refunds and forfeitures.
- `day_sheets.csv` — requested daily centre sheets.
- `command-log.txt` — command outcomes and rejection reasons.

`output\` is generated runtime data and is ignored by Git.

## Project layout

```text
src/main/java/com/ps117/       Java CLI, stream engine, rules, domain and CSV support
src/test/java/com/ps117/       JUnit tests
T055-PS-117-sample-data/       Main command-stream inputs and rule tables
docs/design/                  Design, diagrams, glossary and business-rule register
output/                        Generated runtime reports (ignored)
```

The project also retains batch CLI modes (`PROCESS`, `VALIDATE`, `ALLOCATE`, `QUOTA-REPORT`, `FEE-REPORT`, `DAY-SHEET`, `EVENT-PROCESS`). These modes require `--input-dir DIR`, where `DIR` contains `centres.csv`, `applications.csv`, `documents.csv`, and `events.csv`. No separate batch input dataset is included.

```powershell
java -jar target\T055-PS-117-1.0.0.jar PROCESS --input-dir "C:\path\to\batch-inputs" --output "output"
```

## Design documents

- [Design document](docs/design/DESIGN_DOCUMENT.md)
- [Class diagram](docs/design/CLASS_DIAGRAM.md)
- [State diagram](docs/design/STATE_DIAGRAM.md)
- [Glossary](docs/design/GLOSSARY.md)
- [Business-rule register](docs/design/BUSINESS_RULE_REGISTER.md)
- [Decision log](DECISION_LOG.md)
- [Final audit](FINAL_AUDIT_REPORT.md)
