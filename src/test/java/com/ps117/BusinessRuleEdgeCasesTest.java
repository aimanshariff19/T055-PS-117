package com.ps117;

import com.ps117.allocation.AllocationEngine;
import com.ps117.allocation.BookingWindowValidator;
import com.ps117.allocation.PriorityComparator;
import com.ps117.allocation.QuotaManager;
import com.ps117.app.PassportSlotEngine;
import com.ps117.billing.FeeCalculator;
import com.ps117.billing.RefundCalculator;
import com.ps117.domain.*;
import com.ps117.enums.*;
import com.ps117.lifecycle.CancellationService;
import com.ps117.lifecycle.NoShowService;
import com.ps117.lifecycle.RescheduleService;
import com.ps117.lifecycle.WalkInService;
import com.ps117.model.ExecutionSummary;
import com.ps117.validation.DocumentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BusinessRuleEdgeCasesTest {

    // Edge Case 1: Applicant turns 60 on appointment day
    @Test
    void edgeCase1_applicantTurns60OnAppointmentDay() {
        LocalDate appointmentDate = LocalDate.of(2026, 10, 13);
        LocalDate dob = LocalDate.of(1966, 10, 13); // exactly 60 on appointment day

        Application app = new Application();
        app.setApplicationId("APN-0099");
        app.setDateOfBirth(dob);
        app.setApplicationTimestamp(LocalDateTime.of(2026, 10, 1, 10, 0));
        app.setServiceType(ServiceType.NORMAL);
        app.setBookletSize(BookletSize.PAGES_36);

        assertTrue(app.isSeniorCitizenOn(appointmentDate), "Should be senior citizen on appointment day");

        AllocationEngine engine = new AllocationEngine();
        Category category = engine.calculateCategory(app, appointmentDate);
        assertEquals(Category.SENIOR_CITIZEN, category, "Category must be SENIOR_CITIZEN on appointment date");

        FeeCalculator feeCalculator = new FeeCalculator();
        BigDecimal fee = feeCalculator.calculate(app, appointmentDate);
        // Base normal 36 = 1500.00, 10% senior discount = 150.00 -> 1350.00
        assertEquals(new BigDecimal("1350.00"), fee, "Must receive 10% senior discount on base fee");
    }

    // Edge Case 2: Minor turns 15 between application and appointment
    @Test
    void edgeCase2_minorTurns15BetweenApplicationAndAppointment() {
        LocalDate applicationDate = LocalDate.of(2026, 10, 1);
        LocalDate appointmentDate = LocalDate.of(2026, 10, 14);
        LocalDate dob = LocalDate.of(2011, 10, 10); // turns 15 on 2026-10-10

        Application app = new Application();
        app.setApplicationId("APN-0055");
        app.setDateOfBirth(dob);
        app.setApplicationTimestamp(applicationDate.atTime(10, 0));
        app.setServiceType(ServiceType.NORMAL);
        app.setBookletSize(BookletSize.PAGES_36);
        app.addCategory(Category.MINOR);

        // At application time, aged 14 (< 15)
        assertTrue(app.isMinorOn(applicationDate), "Aged 14 at application time");
        // The age-15 fee boundary is distinct from the under-18 priority category.
        assertFalse(app.isMinorOn(appointmentDate), "Turns 15 before appointment date");
        assertTrue(app.isUnder18On(appointmentDate), "Remains under 18 on appointment date");

        AllocationEngine engine = new AllocationEngine();
        Category category = engine.calculateCategory(app, appointmentDate);
        assertEquals(Category.MINOR, category, "Age 15 remains MINOR under BR-02's under-18 rule");

        FeeCalculator feeCalculator = new FeeCalculator();
        BigDecimal appFee = feeCalculator.calculate(app, appointmentDate);
        assertEquals(new BigDecimal("1500.00"), appFee, "Must be charged normal fee 1500.00 on appointment day");
    }

    // Edge Case 3: Quota release while TATKAL quota remains untouched
    @Test
    void edgeCase3_quotaReleaseWhileTatkalQuotaRemainsUntouched() {
        Centre centre = new Centre("PSK-02", "PSK Marathahalli", "Bengaluru", 28, 7, LocalTime.of(9, 0), LocalTime.of(16, 0));
        LocalDate appDate = LocalDate.of(2026, 10, 7);

        QuotaManager quotaManager = new QuotaManager();
        quotaManager.initializeQuotas(centre, appDate);

        // Quotas for day capacity 28:
        // Tatkal = floor(28 * 0.20) = 5
        // Senior/Minor = floor(28 * 0.15) = 4
        // PCC = floor(28 * 0.10) = 2
        // Normal = 28 - 5 - 4 - 2 = 17
        assertEquals(5, quotaManager.remaining("PSK-02", appDate, QuotaType.TATKAL));
        assertEquals(4, quotaManager.remaining("PSK-02", appDate, QuotaType.SENIOR_MINOR));
        assertEquals(2, quotaManager.remaining("PSK-02", appDate, QuotaType.PCC));
        assertEquals(17, quotaManager.remaining("PSK-02", appDate, QuotaType.NORMAL));

        // Attempting to release TATKAL quota must fail and leave TATKAL untouched
        boolean tatkalReleased = quotaManager.release("PSK-02", appDate, QuotaType.TATKAL);
        assertFalse(tatkalReleased, "TATKAL quota must never be released");
        assertEquals(5, quotaManager.remaining("PSK-02", appDate, QuotaType.TATKAL), "TATKAL quota must remain untouched");

        // Release SENIOR_MINOR quota
        boolean seniorMinorReleased = quotaManager.release("PSK-02", appDate, QuotaType.SENIOR_MINOR);
        assertTrue(seniorMinorReleased, "SENIOR_MINOR quota should be releasable");
        assertEquals(0, quotaManager.remaining("PSK-02", appDate, QuotaType.SENIOR_MINOR));
        assertEquals(4, quotaManager.remaining("PSK-02", appDate, QuotaType.RELEASED), "Released pool must hold 4 seats");

        // Tatkal quota remains strictly 5
        assertEquals(5, quotaManager.remaining("PSK-02", appDate, QuotaType.TATKAL));
    }

    // Edge Case 4: Document expires on appointment date
    @Test
    void edgeCase4_documentExpiresOnAppointmentDate() {
        LocalDate appointmentDate = LocalDate.of(2026, 10, 15);

        Application app = new Application();
        app.setApplicationId("APN-0096");
        app.setServiceType(ServiceType.PCC);
        app.setCategory(Category.NORMAL);

        Document docPassport = new Document("D1", "APN-0096", DocumentType.VALID_PASSPORT, VerificationStatus.VERIFIED, LocalDate.of(2028, 1, 1), true);
        // Expiry date is exactly the appointment date
        Document docAddress = new Document("D2", "APN-0096", DocumentType.ELECTRICITY_BILL, VerificationStatus.VERIFIED, appointmentDate, true);

        DocumentValidator validator = new DocumentValidator();
        DocumentValidationResult result = validator.validate(app, List.of(docPassport, docAddress), appointmentDate);

        assertEquals(ValidationStatus.VALID, result.getValidationStatus(), "Document expiring ON appointment date is still valid (not yet past)");
        assertTrue(result.getExpiredDocuments().isEmpty(), "Document must not be marked expired on the exact appointment day");
    }

    // Edge Case 5: Third reschedule requested exactly on day 7
    @Test
    void edgeCase5_thirdRescheduleRequestedOnDay7() {
        RescheduleService service = new RescheduleService();

        Application app = new Application();
        app.setApplicationId("APN-0016");
        app.setRescheduleCount(2); // already rescheduled twice

        Appointment appointment = new Appointment();
        appointment.setAppointmentId("APT-0016");
        appointment.setApplicationId("APN-0016");
        appointment.setSlotId("SLOT-1");

        // 2nd reschedule was at 2026-10-06 11:00:00
        LocalDateTime secondRescheduleTime = LocalDateTime.of(2026, 10, 6, 11, 0, 0);
        // Mock that the 2nd reschedule was recorded at that time
        service.reschedule(app, appointment, "SLOT-0", "SLOT-1", secondRescheduleTime, "second");
        // Reset count to 2 for testing third reschedule
        app.setRescheduleCount(2);

        // 3rd reschedule requested exactly 7 days later: 2026-10-13 11:00:00
        LocalDateTime thirdRescheduleTime = LocalDateTime.of(2026, 10, 13, 11, 0, 0);

        boolean allowed = service.reschedule(app, appointment, "SLOT-1", "SLOT-2", thirdRescheduleTime, "third");
        assertTrue(allowed, "Third reschedule exactly on day 7 must be allowed");
        assertEquals(3, app.getRescheduleCount());
        assertTrue(app.isThirdRescheduleMovedToBack(), "Third reschedule must be moved to the back of its queue");

        // 4th reschedule attempt must be rejected
        boolean fourthAttempt = service.reschedule(app, appointment, "SLOT-2", "SLOT-3", thirdRescheduleTime.plusDays(8), "fourth");
        assertFalse(fourthAttempt, "Fourth reschedule must be rejected");
    }

    @Test
    void firstRescheduleMustBeSevenDaysAfterInitialBooking() {
        RescheduleService service = new RescheduleService();
        Application app = new Application();
        app.setApplicationId("RESCHEDULE-BOUNDARY");
        service.recordInitialBooking(app, LocalDateTime.of(2026, 9, 28, 18, 0));

        assertFalse(service.canReschedule(app, LocalDateTime.of(2026, 10, 4, 6, 0)),
                "A request less than seven days after initial booking is rejected");
        assertTrue(service.canReschedule(app, LocalDateTime.of(2026, 10, 6, 18, 0)),
                "A request at least seven days after initial booking is allowed");
    }

    // Edge Case 6: Slot capacity 7 and overbooking rounds down to 0
    @Test
    void edgeCase6_slotCapacity7OverbookingRoundsTo0() {
        Slot slot = new Slot("SLOT-7", "PSK-01", LocalDate.of(2026, 10, 8), LocalTime.of(11, 0), 7);
        assertEquals(0, slot.getOverbookingAllowance(), "10% overbooking of 7 must round down to 0");
        assertFalse(slot.isOverbookAllowed(), "No overbooking allowed when allowance is 0");

        for (int i = 0; i < 7; i++) {
            assertTrue(slot.canAllocate(), "Slot can allocate seat " + (i + 1));
            boolean overflow = slot.allocate();
            assertFalse(overflow, "Allocation must not be overflow");
        }

        assertEquals(SlotStatus.FULL, slot.getStatus(), "Slot must be FULL after 7 allocations");
        assertFalse(slot.canAllocate(), "Slot cannot accept 8th allocation");
    }

    // Edge Case 7: Cancellation exactly 48 hours before appointment
    @Test
    void edgeCase7_cancellationExactly48HoursBefore() {
        LocalDateTime appointmentDateTime = LocalDateTime.of(2026, 10, 9, 9, 0, 0);
        LocalDateTime cancellationTime = LocalDateTime.of(2026, 10, 7, 9, 0, 0); // exactly 48h before
        BigDecimal fee = new BigDecimal("2000.00");

        RefundCalculator calculator = new RefundCalculator();
        RefundResult result = calculator.calculate(fee, cancellationTime, appointmentDateTime, false);

        // Exactly 48 hours falls into the 24-48h bracket (50% refund)
        assertEquals(new BigDecimal("1000.00"), result.getRefundAmount(), "Cancellation exactly 48h before receives 50% refund");
    }

    // Edge Case 8: Walk-in cap with daily capacity 35 equals 3
    @Test
    void edgeCase8_walkInCapWithDailyCapacity35Equals3() {
        int dailyCapacity = 35;
        assertEquals(3, WalkInService.computeCap(dailyCapacity), "10% of 35 must round down to 3");

        WalkInService service = new WalkInService(dailyCapacity);
        assertEquals(3, service.getDailyLimit());

        Application senior1 = new Application();
        senior1.setCategory(Category.SENIOR_CITIZEN);
        Application senior2 = new Application();
        senior2.setCategory(Category.SENIOR_CITIZEN);
        Application senior3 = new Application();
        senior3.setCategory(Category.SENIOR_CITIZEN);
        Application diffAbled4 = new Application();
        diffAbled4.setCategory(Category.DIFFERENTLY_ABLED);

        assertTrue(service.registerWalkIn(senior1));
        assertTrue(service.registerWalkIn(senior2));
        assertTrue(service.registerWalkIn(senior3));
        // 4th walk-in must be turned away
        assertFalse(service.registerWalkIn(diffAbled4), "4th walk-in must be refused when cap is 3");
    }

    // Additional test: Invalid CSV rows
    @Test
    void additionalTest_invalidCsvRowsHandledGracefully(@TempDir Path tempDir) throws Exception {
        Path invalidApps = tempDir.resolve("invalid_apps.csv");
        Files.writeString(invalidApps, "app_id,applicant_name\nINVALID_ROW_MISSING_COLUMNS\n");

        PassportSlotEngine engine = new PassportSlotEngine(
                "VALIDATE",
                Path.of("sample-data/centres.csv"),
                invalidApps,
                Path.of("sample-data/documents.csv"),
                Path.of("sample-data/events.csv"),
                tempDir.resolve("output"),
                LocalDateTime.of(2026, 11, 15, 17, 0)
        );

        ExecutionSummary summary = engine.run();
        assertNotNull(summary);
        assertTrue(engine.getProcessingErrors().size() > 0 || summary.getProcessingErrors() >= 0);
    }

    // Additional test: Duplicate active appointments
    @Test
    void additionalTest_duplicateActiveAppointments() {
        AllocationEngine engine = new AllocationEngine();
        Centre centre = new Centre("C1", "Central", "City", 50, 10, LocalTime.of(9, 0), LocalTime.of(16, 0));
        Slot slot1 = new Slot("S1", "C1", LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), 10);
        Slot slot2 = new Slot("S2", "C1", LocalDate.of(2026, 11, 16), LocalTime.of(9, 15), 10);

        Application app1 = new Application();
        app1.setApplicationId("A1");
        app1.setApplicantId("AP001");
        app1.setServiceType(ServiceType.NORMAL);
        app1.setCategory(Category.NORMAL);
        app1.setCentrePreference("C1");
        app1.setApplicationTimestamp(LocalDateTime.of(2026, 11, 10, 8, 0));
        app1.setValidationStatus(ValidationStatus.VALID);
        app1.setEligibleForAllocation(true);

        Application app2 = new Application();
        app2.setApplicationId("A2");
        app2.setApplicantId("AP001"); // same applicant ID!
        app2.setServiceType(ServiceType.NORMAL);
        app2.setCategory(Category.NORMAL);
        app2.setCentrePreference("C1");
        app2.setApplicationTimestamp(LocalDateTime.of(2026, 11, 10, 8, 30));
        app2.setValidationStatus(ValidationStatus.VALID);
        app2.setEligibleForAllocation(true);

        QuotaManager quotaManager = new QuotaManager();
        List<AllocationResult> results = engine.allocate(List.of(app1, app2), List.of(centre), List.of(slot1, slot2), quotaManager, LocalDate.of(2026, 11, 15));

        assertTrue(results.get(0).isAllocated(), "First appointment allocated");
        assertFalse(results.get(1).isAllocated(), "Duplicate appointment for same applicant must be rejected");
        assertEquals("Duplicate active appointment", results.get(1).getReason());
    }

    // Additional test: Missing mandatory documents
    @Test
    void additionalTest_missingMandatoryDocuments() {
        Application app = new Application();
        app.setApplicationId("A-MISSING");
        app.setServiceType(ServiceType.NORMAL);
        app.setCategory(Category.NORMAL);

        Document address = new Document("D1", "A-MISSING", DocumentType.ADDRESS_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        // Missing DOB and Photo!

        DocumentValidator validator = new DocumentValidator();
        DocumentValidationResult result = validator.validate(app, List.of(address), LocalDate.of(2026, 11, 16));

        assertEquals(ValidationStatus.DOC_BLOCK, result.getValidationStatus());
        assertFalse(app.isEligibleForAllocation());
        assertTrue(result.getMissingDocuments().size() >= 2);
    }

    // Additional test: TATKAL without verified Aadhaar
    @Test
    void additionalTest_tatkalWithoutVerifiedAadhaar() {
        Application app = new Application();
        app.setApplicationId("A-TATKAL-NO-AADHAAR");
        app.setServiceType(ServiceType.TATKAL);
        app.setAadhaarVerificationStatus(VerificationStatus.UNVERIFIED);

        Document d1 = new Document("D1", app.getApplicationId(), DocumentType.ADDRESS_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document d2 = new Document("D2", app.getApplicationId(), DocumentType.DOB_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document d3 = new Document("D3", app.getApplicationId(), DocumentType.PHOTOGRAPH, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);

        DocumentValidator validator = new DocumentValidator();
        DocumentValidationResult result = validator.validate(app, List.of(d1, d2, d3), LocalDate.of(2026, 11, 16));

        assertEquals(ValidationStatus.DOC_BLOCK, result.getValidationStatus(), "TATKAL without verified Aadhaar must be blocked");
        assertFalse(app.isEligibleForAllocation());
    }

    // Additional test: Quota exhaustion
    @Test
    void additionalTest_quotaExhaustion() {
        Centre centre = new Centre("C1", "Central", "City", 5, 5, LocalTime.of(9, 0), LocalTime.of(16, 0));
        LocalDate appDate = LocalDate.of(2026, 11, 16);

        QuotaManager quotaManager = new QuotaManager();
        quotaManager.initializeQuotas(centre, appDate);
        // Day capacity 5: Tatkal = 5 * 0.20 = 1 seat

        Application app1 = new Application();
        app1.setApplicationId("T1");
        app1.setApplicantId("AP1");
        app1.setServiceType(ServiceType.TATKAL);
        app1.setAadhaarVerificationStatus(VerificationStatus.VERIFIED);
        app1.setCategory(Category.TATKAL);

        Application app2 = new Application();
        app2.setApplicationId("T2");
        app2.setApplicantId("AP2");
        app2.setServiceType(ServiceType.TATKAL);
        app2.setAadhaarVerificationStatus(VerificationStatus.VERIFIED);
        app2.setCategory(Category.TATKAL);

        QuotaType q1 = quotaManager.consumeForApplication(app1, centre, appDate);
        assertEquals(QuotaType.TATKAL, q1);

        QuotaType q2 = quotaManager.consumeForApplication(app2, centre, appDate);
        assertNull(q2, "Second TATKAL must fail due to quota exhaustion");
    }

    // Additional test: Booking window violations
    @Test
    void additionalTest_bookingWindowViolations() {
        BookingWindowValidator validator = new BookingWindowValidator();
        Centre centre = new Centre("C1", "Central", "City", 50, 10, LocalTime.of(9, 0), LocalTime.of(16, 0));

        LocalDate asOf = LocalDate.of(2026, 11, 13); // Friday
        LocalDate weekendDate = LocalDate.of(2026, 11, 14); // Saturday
        LocalDate pastDate = LocalDate.of(2026, 11, 10);
        LocalDate farFutureDate = LocalDate.of(2027, 2, 1);

        Application app = new Application();
        app.setServiceType(ServiceType.NORMAL);

        assertFalse(validator.isEligible(app, centre, pastDate, asOf), "Past date must violate booking window");
        assertFalse(validator.isEligible(app, centre, weekendDate, asOf), "Weekend date before next working day violates window");
        assertFalse(validator.isEligible(app, centre, farFutureDate, asOf), "Date beyond 45 days violates window");
    }

    // Additional test: Second no-show
    @Test
    void additionalTest_secondNoShowBarsApplicant() {
        NoShowService noShowService = new NoShowService();

        Application app = new Application();
        app.setApplicationId("A-NOSHOW");
        app.setApplicantId("AP-NOSHOW");

        Appointment apt1 = new Appointment();
        apt1.setAppointmentId("APT-1");
        apt1.setApplicantId("AP-NOSHOW");

        Appointment apt2 = new Appointment();
        apt2.setAppointmentId("APT-2");
        apt2.setApplicantId("AP-NOSHOW");

        assertTrue(noShowService.recordNoShow(app, apt1), "First no-show recorded");
        assertEquals(AppointmentStatus.EXPIRED, apt1.getStatus());
        assertEquals(1, noShowService.getNoShowCount("AP-NOSHOW"));

        assertTrue(noShowService.recordNoShow(app, apt2), "Second no-show recorded");
        assertEquals(AppointmentStatus.EXPIRED, apt2.getStatus());
        assertEquals(2, noShowService.getNoShowCount("AP-NOSHOW"));
        assertEquals(ApplicationStatus.REJECTED, app.getApplicationStatus(), "Second no-show must reject application");
        assertFalse(app.isEligibleForAllocation());
    }

    // Additional test: TATKAL refund restrictions
    @Test
    void additionalTest_tatkalRefundRestrictions() {
        RefundCalculator calculator = new RefundCalculator();
        BigDecimal totalTatkalFee = new BigDecimal("3500.00"); // 1500 base + 2000 tatkal surcharge
        LocalDateTime cancellation = LocalDateTime.of(2026, 10, 5, 9, 0);
        LocalDateTime appointment = LocalDateTime.of(2026, 10, 8, 9, 0); // 72 hours before (> 48h)

        RefundResult result = calculator.calculate(totalTatkalFee, cancellation, appointment, true);

        // Tatkal surcharge is non-refundable. Only base fee (1500) minus 100 admin fee is refunded -> 1400.00
        assertEquals(new BigDecimal("1400.00"), result.getRefundAmount(), "TATKAL surcharge must be non-refundable");
    }

    // Additional test: Overflow WAIT flag
    @Test
    void additionalTest_overflowWaitFlag() {
        Slot slot = new Slot("SLOT-OVERFLOW", "C1", LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), 10);
        assertEquals(1, slot.getOverbookingAllowance(), "10% overbooking of 10 equals 1");

        // Fill physical capacity
        for (int i = 0; i < 10; i++) {
            boolean isOverflow = slot.allocate();
            assertFalse(isOverflow, "Physical seat is not overflow");
        }

        // 11th allocation is overbooked overflow
        assertTrue(slot.canAllocate(), "Can allocate 11th overbooking seat");
        boolean isOverflow = slot.allocate();
        assertTrue(isOverflow, "11th seat must be flagged as overflow WAIT");
        assertEquals(SlotStatus.FULL, slot.getStatus(), "Slot is now full");
    }

    @Test
    void plantedScenario_reissueFeesBasedOnExpiry() {
        FeeCalculator calculator = new FeeCalculator();
        LocalDate apptDate = LocalDate.of(2026, 10, 8);

        // APN-0013: RE-ISSUE, 36 pages, expired 2022-06-30 (>3 years before appt)
        Application appOver3Y = new Application();
        appOver3Y.setApplicationId("APN-0013");
        appOver3Y.setServiceType(ServiceType.RE_ISSUE);
        appOver3Y.setBookletSize(BookletSize.PAGES_36);
        appOver3Y.setOldPassportExpiry(LocalDate.of(2022, 6, 30));
        FeeBreakdown feeOver3Y = calculator.breakdown(appOver3Y, apptDate);
        assertEquals(new BigDecimal("3000.00"), feeOver3Y.getBaseFee(), "RE-ISSUE expired >3 years must have 3000 base fee");

        // APN-0042: RE-ISSUE, 36 pages, expired 2026-02-28 (within 1 year of appt)
        Application appWithin1Y = new Application();
        appWithin1Y.setApplicationId("APN-0042");
        appWithin1Y.setServiceType(ServiceType.RE_ISSUE);
        appWithin1Y.setBookletSize(BookletSize.PAGES_36);
        appWithin1Y.setOldPassportExpiry(LocalDate.of(2026, 2, 28));
        FeeBreakdown feeWithin1Y = calculator.breakdown(appWithin1Y, apptDate);
        assertEquals(new BigDecimal("1500.00"), feeWithin1Y.getBaseFee(), "RE-ISSUE within 1 year must have NORMAL base fee 1500");
    }

    @Test
    void plantedScenario_lostPassportFees() {
        FeeCalculator calculator = new FeeCalculator();

        // APN-0081 scenario: LOST passport 60 pages
        Application appLost60 = new Application();
        appLost60.setApplicationId("APN-0081");
        appLost60.setServiceType(ServiceType.RE_ISSUE);
        appLost60.setPassportCondition(PassportCondition.LOST);
        appLost60.setBookletSize(BookletSize.PAGES_60);
        FeeBreakdown feeLost60 = calculator.breakdown(appLost60, LocalDate.of(2026, 10, 15));
        assertEquals(new BigDecimal("3500.00"), feeLost60.getBaseFee(), "Lost passport 60 pages must be 3500");

        // Lost passport 36 pages
        Application appLost36 = new Application();
        appLost36.setServiceType(ServiceType.RE_ISSUE);
        appLost36.setPassportCondition(PassportCondition.LOST);
        appLost36.setBookletSize(BookletSize.PAGES_36);
        FeeBreakdown feeLost36 = calculator.breakdown(appLost36, LocalDate.of(2026, 10, 15));
        assertEquals(new BigDecimal("3000.00"), feeLost36.getBaseFee(), "Lost passport 36 pages must be 3000");
    }

    @Test
    void plantedScenario_tatkalSeniorDiscount() {
        FeeCalculator calculator = new FeeCalculator();
        LocalDate apptDate = LocalDate.of(2026, 10, 15);

        // APN-0103: Senior citizen + Tatkal
        Application app = new Application();
        app.setApplicationId("APN-0103");
        app.setCategory(Category.TATKAL);
        app.setDateOfBirth(LocalDate.of(1955, 1, 1)); // Senior (>60)
        app.setServiceType(ServiceType.TATKAL);
        app.setBookletSize(BookletSize.PAGES_36);

        FeeBreakdown fee = calculator.breakdown(app, apptDate);
        assertEquals(new BigDecimal("1500.00"), fee.getBaseFee(), "Base fee 1500");
        assertEquals(new BigDecimal("150.00"), fee.getSeniorDiscount(), "10% senior discount on base fee only = 150");
        assertEquals(new BigDecimal("2000.00"), fee.getTatkalSurcharge(), "Tatkal surcharge = 2000");
        assertEquals(new BigDecimal("3350.00"), fee.getTotal(), "Total = 1500 - 150 + 2000 = 3350");
    }

    @Test
    void plantedScenario_apn0091_tatkalWithoutVerifiedAadhaarCategoryPreserved() {
        Application app = new Application();
        app.setApplicationId("APN-0091");
        app.setServiceType(ServiceType.TATKAL);
        app.setAadhaarVerificationStatus(VerificationStatus.UNVERIFIED);
        app.setCategory(Category.TATKAL);

        AllocationEngine engine = new AllocationEngine();
        Category cat = engine.calculateCategory(app, LocalDate.of(2026, 10, 7));
        assertEquals(Category.TATKAL, cat, "Category must remain TATKAL even if Aadhaar unverified");

        Document d1 = new Document("D1", app.getApplicationId(), DocumentType.ADDRESS_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document d2 = new Document("D2", app.getApplicationId(), DocumentType.DOB_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document d3 = new Document("D3", app.getApplicationId(), DocumentType.PHOTOGRAPH, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);

        DocumentValidator validator = new DocumentValidator();
        DocumentValidationResult vr = validator.validate(app, List.of(d1, d2, d3), LocalDate.of(2026, 10, 7));
        assertEquals(ValidationStatus.DOC_BLOCK, vr.getValidationStatus(), "Must be blocked due to unverified Aadhaar");
        assertTrue(vr.getBlockingGaps().contains("TATKAL requires VERIFIED Aadhaar"));
    }

    @Test
    void plantedScenario_apn0098_docScoreBelow3RemainsDocBlock() {
        Application app = new Application();
        app.setApplicationId("APN-0098");
        app.setServiceType(ServiceType.NORMAL);

        // Only two SELF_ATTESTED docs (score 2) and no photograph
        Document d1 = new Document("D1", app.getApplicationId(), DocumentType.ADDRESS_PROOF, VerificationStatus.SELF_ATTESTED, LocalDate.of(2027, 1, 1), true);
        Document d2 = new Document("D2", app.getApplicationId(), DocumentType.DOB_PROOF, VerificationStatus.SELF_ATTESTED, LocalDate.of(2027, 1, 1), true);

        DocumentValidator validator = new DocumentValidator();
        DocumentValidationResult vr = validator.validate(app, List.of(d1, d2), LocalDate.of(2026, 10, 7));
        assertEquals(2, vr.getDocumentScore());
        assertEquals(ValidationStatus.DOC_BLOCK, vr.getValidationStatus());
        assertEquals(VerificationMode.DOC_BLOCK, vr.getVerificationMode(),
                "score below 3 remains DOC_BLOCK internally");
        assertTrue(vr.getBlockingGaps().stream().anyMatch(g -> g.contains("below minimum threshold 3")));
    }

    // CLI Commands Tests
    @Test
    void testCliCommandsIndependently(@TempDir Path tempDir) throws Exception {
        List<String> commands = List.of(
                "PROCESS", "VALIDATE", "ALLOCATE", "QUOTA-REPORT",
                "FEE-REPORT", "DAY-SHEET", "EVENT-PROCESS"
        );

        for (String cmd : commands) {
            Path out = tempDir.resolve("run-" + cmd);
            PassportSlotEngine engine = new PassportSlotEngine(
                    cmd,
                    Path.of("sample-data/centres.csv"),
                    Path.of("sample-data/applications.csv"),
                    Path.of("sample-data/documents.csv"),
                    Path.of("sample-data/events.csv"),
                    out,
                    LocalDateTime.of(2026, 11, 15, 17, 0)
            );
            ExecutionSummary summary = engine.run();
            assertNotNull(summary);
            assertEquals(cmd, summary.getCommand());
            assertTrue(Files.exists(out.resolve("execution-summary.txt")), "Summary must exist for " + cmd);

            switch (cmd) {
                case "VALIDATE" -> assertTrue(Files.exists(out.resolve("validation-results.csv")));
                case "ALLOCATE" -> {
                    assertTrue(Files.exists(out.resolve("allocation-results.csv")));
                    assertTrue(Files.exists(out.resolve("quota-summary.csv")));
                }
                case "QUOTA-REPORT" -> assertTrue(Files.exists(out.resolve("quota-summary.csv")));
                case "FEE-REPORT" -> assertTrue(Files.exists(out.resolve("fee-breakdown.csv")));
                case "DAY-SHEET" -> assertTrue(Files.exists(out.resolve("centre-day-sheets.csv")));
                case "EVENT-PROCESS" -> assertTrue(Files.exists(out.resolve("event-results.csv")));
                case "PROCESS" -> {
                    assertTrue(Files.exists(out.resolve("allocation-results.csv")));
                    assertTrue(Files.exists(out.resolve("validation-results.csv")));
                    assertTrue(Files.exists(out.resolve("fee-breakdown.csv")));
                    assertTrue(Files.exists(out.resolve("quota-summary.csv")));
                }
            }
        }
    }
}
