# Design Document

## Problem statement
The system manages passport appointment centres, validates documents, allocates slots, calculates fees, processes reschedules and reports daily activity.

## Architecture
The code follows a layered design: domain, validation, allocation, lifecycle, billing, reporting, infrastructure and CLI.

## Business rules
- 28 slots each day from 09:00 to 16:00
- Quotas are tracked independently per centre and type
- Document validation is separate from police verification scoring
- Appointment reschedules are logged and audited
- Refunds use BigDecimal calculations

## Testing strategy
JUnit 5 tests cover allocation, validation, fees and event processing.
