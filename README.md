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

## Project structure
- `src/main/java/com/ps117` production code
- `src/main/resources/application.properties` default application configuration
- `src/test/java/com/ps117` tests
- `sample-data` sample CSV files
- `output` generated reports
