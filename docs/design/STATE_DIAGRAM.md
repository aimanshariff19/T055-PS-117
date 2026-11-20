# Appointment State Diagram

Application validation/allocation and appointment lifecycle are separate states in the implementation. A no-show sets the appointment to `EXPIRED`; the second no-show also sets the application to `REJECTED`. Rebooking creates a new appointment record.

```mermaid
stateDiagram-v2
    state Application {
        [*] --> SUBMITTED
        SUBMITTED --> VALIDATED : documents pass
        SUBMITTED --> DOC_BLOCK : mandatory gap or score < 3
        VALIDATED --> ALLOCATED : eligible allocation
        VALIDATED --> ALLOCATED : eligible walk-in served
        ALLOCATED --> REJECTED : second no-show
        DOC_BLOCK --> [*] : not allocated
        REJECTED --> [*]
    }

    state Appointment {
        [*] --> BOOKED : allocation or rebook
        [*] --> WAIT : overbooking allocation
        BOOKED --> RESCHEDULED : new slot assigned
        WAIT --> RESCHEDULED : reschedule accepted
        BOOKED --> CANCELLED : cancel
        WAIT --> CANCELLED : cancel
        BOOKED --> EXPIRED : no-show
        WAIT --> EXPIRED : no-show
        RESCHEDULED --> CANCELLED : cancel
        RESCHEDULED --> EXPIRED : no-show
        EXPIRED --> [*] : retained in history
        CANCELLED --> [*] : retained in history
    }

    state WalkInOutcome {
        [*] --> WALK_IN_SERVED : eligible, valid documents, vacant slot and cap available
        [*] --> WALK_IN_REFUSED : ineligible, validation failure, full cap or no vacant slot
        WALK_IN_SERVED --> [*]
        WALK_IN_REFUSED --> [*]
    }
```

`COMPLETED` is declared in `AppointmentStatus`, but the command stream has no attendance/completion command, so no transition to it is shown. Walk-in outcomes are report actions rather than appointment enum values.
