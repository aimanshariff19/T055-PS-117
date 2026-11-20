# Business Rule Register

Implementation and test mapping for the rule IDs documented by this project. A passing unit test verifies only its stated condition; it does not prove complete rule coverage.

| Rule | Implementation | Test evidence | Status / note |
|---|---|---|---|
| BR-01 Quotas and release | `QuotaManager.initializeQuotas`, `releaseAllEligible`; `CommandStreamEngine.triggerQuotaReleasesUpTo` | `BusinessRuleEdgeCasesTest.edgeCase3_quotaReleaseWhileTatkalQuotaRemainsUntouched` | Unit-tested; included in stream execution |
| BR-02 Priority and FIFO | `AllocationEngine.calculateCategory`; `PriorityComparator.compare` | `edgeCase1_applicantTurns60OnAppointmentDay`; allocation tests | Tested; category and age boundary cases included |
| BR-03 Document checklist and score | `DocumentValidator.validate`, `DocumentChecklist`, `DocumentScorer` | `DocumentValidatorTest`; `BusinessRuleEdgeCasesTest.plantedScenario_apn0098_docScoreBelow3RemainsDocBlock` | Tested; output schema conflict tracked in DEC-013 |
| BR-04 Booking windows | `BookingWindowValidator.isEligible` | `BusinessRuleEdgeCasesTest.additionalTest_bookingWindowViolations` | Unit-tested |
| BR-05 Fees and concession | `FeeCalculator.breakdown` | `FeeCalculatorTest`; reissue, lost/damaged and Tatkal-senior edge tests | Tested for listed cases |
| BR-06 Rescheduling | `RescheduleService.recordInitialBooking`, `canReschedule`, `reschedule`; stream command handler | `BusinessRuleEdgeCasesTest.edgeCase5_thirdRescheduleRequestedOnDay7`; `firstRescheduleMustBeSevenDaysAfterInitialBooking` | Unit-tested. APN-0016's first request is rejected; the later two succeed, so its stream sequence contains two successful reschedules rather than three. See DEC-015. |
| BR-07 No-show and rebooking | `CommandStreamEngine.handleNoShow`, `handleRebook`; `NoShowService` | `BusinessRuleEdgeCasesTest.additionalTest_secondNoShowBarsApplicant`; sample APN-0032 | Tested; sample records first and second no-show |
| BR-08 Walk-ins | `CommandStreamEngine.handleWalkIn`; `WalkInService` | `BusinessRuleEdgeCasesTest.edgeCase8_walkInCapWithDailyCapacity35Equals3`; sample stream | Cap tested; stream also validates APN-0019 documents at walk-in time |
| BR-09 Cancellation refunds | `RefundCalculator.calculate`; `CommandStreamEngine.handleCancel` | `BusinessRuleEdgeCasesTest.edgeCase7_cancellationExactly48HoursBefore`, Tatkal refund test | Tested at boundary and Tatkal condition |
| BR-10 Overbooking | `Slot.getOverbookingAllowance`, `Slot.allocate` | `BusinessRuleEdgeCasesTest.edgeCase6_slotCapacity7OverbookingRoundsTo0`, `additionalTest_overflowWaitFlag` | Unit-tested |
| BR-11 Police verification | `VerificationModeResolver.resolve`; `CommandStreamEngine.writeValidationCsv` | `DocumentValidatorTest.scoreBelowVerificationThresholdRemainsDocBlockMode`; APN-0098 scenario | Internal mode tested; CSV maps internal `DOC_BLOCK` to `PRE` per binding enum. See DEC-013. |
| BR-12 Ordered command stream | `CommandStreamEngine.processCommandFile`, command parser and handlers | Actual 266-command sample stream | Stream execution verifies order and outcomes; malformed command behavior is not separately covered by a dedicated test |

## Test command

```powershell
& .\mvnw.cmd test
```
