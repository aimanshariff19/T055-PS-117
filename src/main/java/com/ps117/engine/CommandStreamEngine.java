package com.ps117.engine;

import com.ps117.allocation.AllocationEngine;
import com.ps117.allocation.BookingWindowValidator;
import com.ps117.allocation.QuotaManager;
import com.ps117.billing.FeeCalculator;
import com.ps117.domain.*;
import com.ps117.enums.*;
import com.ps117.infrastructure.CsvReader;
import com.ps117.infrastructure.CsvWriter;
import com.ps117.lifecycle.RescheduleService;
import com.ps117.util.DateUtils;
import com.ps117.util.MoneyUtils;
import com.ps117.validation.DocumentValidator;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command-stream engine that processes commands.txt sequentially.
 * Maintains a simulation clock and proper state across all commands.
 *
 * Supported commands:
 *   RUN_ALLOCATION|run_ts
 *   RESCHEDULE|app_id|request_ts|new_preferred_date|band
 *   CANCEL|app_id|request_ts
 *   NO_SHOW|app_id|appointment_date
 *   REBOOK|app_id|request_ts|preferred_date|band
 *   WALK_IN|app_id|centre_id|arrival_ts
 *   PRINT_APPLICATION|app_id
 *   PRINT_DAY_SHEET|centre_id|date
 */
public class CommandStreamEngine {

    // --- data loaded once ---
    private final List<Centre> centres = new ArrayList<>();
    private final List<Application> applications = new ArrayList<>();
    private final List<Document> documents = new ArrayList<>();
    private final Set<LocalDate> holidays = new LinkedHashSet<>();
    // slot capacity from slot_capacity.csv: key = centreId|date|time -> capacity
    private final Map<String, Integer> slotCapacityMap = new HashMap<>();

    // --- mutable simulation state ---
    private final Map<String, Slot> slots = new LinkedHashMap<>();      // slotId -> Slot
    private final Map<String, Appointment> appointments = new LinkedHashMap<>(); // appId -> active Appointment
    private final Map<String, List<Appointment>> appointmentHistory = new LinkedHashMap<>(); // appId -> all appointments
    private final Map<String, DocumentValidationResult> validationResults = new LinkedHashMap<>();
    private final Map<String, FeeBreakdown> feeBreakdowns = new LinkedHashMap<>();
    private final Map<String, BigDecimal> refundAmounts = new LinkedHashMap<>();
    private final Map<String, Boolean> forfeitedFees = new LinkedHashMap<>();

    // quota manager per date per centre
    private final QuotaManager quotaManager = new QuotaManager();
    // track quota pools used per appointment
    private final Map<String, QuotaType> appointmentQuotaPool = new LinkedHashMap<>();
    // track priority reasons per appointment
    private final Map<String, String> appointmentPriorityReason = new LinkedHashMap<>();

    // --- evaluation output rows ---
    private final List<String[]> appointmentsRows = new ArrayList<>(); // appointments.csv rows
    private final List<String[]> daySheetRows = new ArrayList<>();     // day_sheets.csv rows

    // --- services ---
    private final FeeCalculator feeCalculator = new FeeCalculator();
    private final RescheduleService rescheduleService = new RescheduleService();
    private final BookingWindowValidator bookingWindowValidator;
    private final DocumentValidator documentValidator = new DocumentValidator();
    private final AllocationEngine allocationEngine = new AllocationEngine();

    // simulation clock
    private LocalDateTime simulationClock = LocalDateTime.of(2026, 9, 1, 0, 0);

    // quota release tracking: appointmentDate -> already released?
    private final Set<String> releasedDates = new HashSet<>();

    // per-centre walk-in counts for each date
    private final Map<String, Integer> walkInCounts = new HashMap<>(); // key: centreId|date

    // set of allocated appIds (to prevent duplicate active allocation)
    private final Set<String> allocatedAppIds = new HashSet<>();

    // rebook tracking
    private final Map<String, Integer> noShowCounts = new HashMap<>(); // appId -> noshow count
    private final Map<String, LocalDate> lastNoShowDate = new HashMap<>();

    // command stream output
    private final List<String> commandLog = new ArrayList<>();
    private int commandCount = 0;

    public CommandStreamEngine(
            Path centresPath, Path applicationsPath, Path documentsPath,
            Path holidaysPath, Path slotCapacityPath) throws Exception {
        // Load holidays
        if (holidaysPath != null && Files.exists(holidaysPath)) {
            loadHolidays(holidaysPath);
        }
        bookingWindowValidator = new BookingWindowValidator();
        for (LocalDate h : holidays) {
            bookingWindowValidator.addHoliday(h);
        }

        if (centresPath != null && Files.exists(centresPath)) loadCentres(centresPath);
        if (applicationsPath != null && Files.exists(applicationsPath)) loadApplications(applicationsPath);
        if (documentsPath != null && Files.exists(documentsPath)) loadDocuments(documentsPath);
        if (slotCapacityPath != null && Files.exists(slotCapacityPath)) loadSlotCapacity(slotCapacityPath);

        // Pre-validate all applications (document check)
        preValidateAll();
    }

    // ==================== MAIN ENTRY POINT ====================

    public void processCommandFile(Path commandsPath, Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        for (String fileName : List.of(
                "validation.csv", "appointments.csv", "fees.csv", "day_sheets.csv", "command-log.txt")) {
            Files.deleteIfExists(outputDir.resolve(fileName));
        }

        List<String[]> parsedCommands = parseCommandFile(commandsPath);
        commandCount = parsedCommands.size();

        for (String[] cmd : parsedCommands) {
            String cmdName = cmd[0];

            // Advance simulation clock to this command's timestamp (if applicable)
            LocalDateTime cmdTime = extractTimestamp(cmd);
            if (cmdTime != null && cmdTime.isAfter(simulationClock)) {
                // Before advancing clock, check if any quota releases are due
                triggerQuotaReleasesUpTo(cmdTime);
                simulationClock = cmdTime;
            } else if (cmdTime == null) {
                // Commands without timestamps (PRINT_*) use current clock
            }

            try {
                switch (cmdName) {
                    case "RUN_ALLOCATION" -> handleRunAllocation(cmd);
                    case "RESCHEDULE"     -> handleReschedule(cmd);
                    case "CANCEL"         -> handleCancel(cmd);
                    case "NO_SHOW"        -> handleNoShow(cmd);
                    case "REBOOK"         -> handleRebook(cmd);
                    case "WALK_IN"        -> handleWalkIn(cmd);
                    case "PRINT_APPLICATION" -> handlePrintApplication(cmd);
                    case "PRINT_DAY_SHEET"   -> handlePrintDaySheet(cmd);
                    default -> {
                        commandLog.add("SKIPPED|" + String.join("|", cmd) + "|Unknown command");
                    }
                }
            } catch (Exception e) {
                commandLog.add("ERROR|" + String.join("|", cmd) + "|" + e.getMessage());
            }
        }

        if (commandLog.size() != commandCount) {
            throw new IllegalStateException("Expected one recorded outcome per command, but found "
                    + commandLog.size() + " outcomes for " + commandCount + " commands.");
        }

        // Write evaluation CSVs
        writeValidationCsv(outputDir);
        writeAppointmentsCsv(outputDir);
        writeFeesCsv(outputDir);
        writeDaySheetsCsv(outputDir);
        writeCommandLog(outputDir);

        // Print readable execution summary in terminal
        printExecutionSummary(outputDir);
    }

    private void printExecutionSummary(Path outputDir) {
        System.out.println("Project: T055-PS-117");
        long allocatedCount = appointmentsRows.stream().filter(r -> "ALLOCATED".equals(r[1])).count();
        long docBlockCount = validationResults.values().stream().filter(r -> r.getValidationStatus() == ValidationStatus.DOC_BLOCK).count();
        long cancelCount = appointmentsRows.stream().filter(r -> "CANCELLED".equals(r[1])).count();
        long rescheduleCount = appointmentsRows.stream().filter(r -> "RESCHEDULED".equals(r[1])).count();
        long noShowCount = appointmentsRows.stream().filter(r -> "NO_SHOW".equals(r[1])).count();
        long walkInServedCount = appointmentsRows.stream().filter(r -> "WALK_IN_SERVED".equals(r[1])).count();
        long walkInRefusedCount = appointmentsRows.stream().filter(r -> "WALK_IN_REFUSED".equals(r[1])).count();

        List<String> unsuccessfulLogs = commandLog.stream().filter(this::isUnsuccessfulCommand).toList();
        long successfulCmds = commandCount - unsuccessfulLogs.size();

        BigDecimal totalFees = feeBreakdowns.values().stream().map(FeeBreakdown::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRefunds = refundAmounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long forfeitedCount = forfeitedFees.values().stream().filter(Boolean::booleanValue).count();

        System.out.println("================================================================================");
        System.out.println("                   PS-117 SIMULATION EXECUTION SUMMARY                          ");
        System.out.println("================================================================================");
        System.out.printf("  Applications loaded:               %d%n", applications.size());
        System.out.printf("  Documents loaded:                  %d%n", documents.size());
        System.out.printf("  Commands processed:                %d%n", commandCount);
        System.out.printf("  Successful commands:               %d%n", successfulCmds);
        System.out.printf("  Rejected / refused / failed:       %d%n", unsuccessfulLogs.size());
        System.out.println("  ------------------------------------------------------------------------------");
        System.out.printf("  Appointments allocated:            %d%n", allocatedCount);
        System.out.printf("  Document-blocked applications:     %d%n", docBlockCount);
        System.out.printf("  Cancellations:                     %d%n", cancelCount);
        System.out.printf("  Reschedules:                       %d%n", rescheduleCount);
        System.out.printf("  No-shows:                          %d%n", noShowCount);
        System.out.printf("  Walk-ins served:                   %d%n", walkInServedCount);
        System.out.printf("  Walk-ins refused:                  %d%n", walkInRefusedCount);
        System.out.println("  ------------------------------------------------------------------------------");
        System.out.printf("  Total fees assessed:               Rs %s%n", totalFees.toPlainString());
        System.out.printf("  Total refunds issued:              Rs %s%n", totalRefunds.toPlainString());
        System.out.printf("  Applications forfeited:            %d%n", forfeitedCount);
        System.out.println("  ------------------------------------------------------------------------------");
        System.out.println("  Sample rejected commands & reasons:");
        unsuccessfulLogs.stream().limit(6).forEach(l -> System.out.println("    * " + l));
        if (unsuccessfulLogs.size() > 6) {
            System.out.printf("    ... and %d more (see command-log.txt)%n", unsuccessfulLogs.size() - 6);
        }
        System.out.println("  ------------------------------------------------------------------------------");
        System.out.printf("  Output directory:                  %s%n", outputDir.toAbsolutePath());
        System.out.println("  Generated files:                   validation.csv, appointments.csv,");
        System.out.println("                                     fees.csv, day_sheets.csv, command-log.txt");
        System.out.println("================================================================================");
    }

    private boolean isUnsuccessfulCommand(String line) {
        return line.contains("|REJECTED|") || line.contains("|REFUSED|")
                || line.contains("|BLOCKED|") || line.contains("|ERROR|") || line.startsWith("SKIPPED|");
    }

    // ==================== COMMAND HANDLERS ====================

    private void handleRunAllocation(String[] cmd) {
        // RUN_ALLOCATION|run_ts
        LocalDateTime runTs = parseDateTime(cmd[1]);
        simulationClock = runTs;
        int allocationsBefore = allocatedAppIds.size();

        // Trigger quota releases before this allocation run
        triggerQuotaReleasesUpTo(runTs);

        // Find applications eligible at this timestamp
        List<Application> eligible = applications.stream()
                .filter(a -> a.getApplicationTimestamp() != null && !a.getApplicationTimestamp().isAfter(runTs))
                .filter(a -> a.getChannel() != Channel.WALK_IN)
                .filter(a -> a.isEligibleForAllocation())
                .filter(a -> !allocatedAppIds.contains(a.getApplicationId()))
                .filter(a -> a.getValidationStatus() == ValidationStatus.VALID ||
                             a.getApplicationStatus() == ApplicationStatus.VALIDATED)
                .collect(Collectors.toList());

        // For each eligible app, determine appointment date = preferred_date (if valid) else next working day
        // Sort by priority
        for (Application app : eligible) {
            // Compute category on-the-fly based on preferred_date
            LocalDate preferredDate = app.getPreferredDate();
            LocalDate appointmentDate = resolveAppointmentDate(app, runTs.toLocalDate(), preferredDate);
            if (appointmentDate == null) continue;

            // Recalculate category on appointment date
            Category cat = allocationEngine.calculateCategory(app, appointmentDate);
            app.setCategory(cat);
        }

        // Sort by priority (TATKAL > SENIOR_CITIZEN > MINOR > DIFFERENTLY_ABLED > NORMAL, then timestamp)
        eligible.sort((a, b) -> {
            int rankA = priorityRank(a.getCategory()) * 10 + (a.isThirdRescheduleMovedToBack() ? 1 : 0);
            int rankB = priorityRank(b.getCategory()) * 10 + (b.isThirdRescheduleMovedToBack() ? 1 : 0);
            if (rankA != rankB) return rankA - rankB;
            if (a.getApplicationTimestamp() != null && b.getApplicationTimestamp() != null) {
                int c = a.getApplicationTimestamp().compareTo(b.getApplicationTimestamp());
                if (c != 0) return c;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(
                    a.getApplicationId() == null ? "" : a.getApplicationId(),
                    b.getApplicationId() == null ? "" : b.getApplicationId());
        });

        for (Application app : eligible) {
            tryAllocate(app, runTs);
        }

        int allocationsMade = allocatedAppIds.size() - allocationsBefore;
        commandLog.add("RUN_ALLOCATION|" + formatDateTime(runTs)
                + "|OK|eligible=" + eligible.size() + "|allocated=" + allocationsMade);
    }

    private void tryAllocate(Application app, LocalDateTime runTs) {
        String appId = app.getApplicationId();

        // Check TATKAL Aadhaar requirement
        if (app.getServiceType() == ServiceType.TATKAL) {
            if (app.getAadhaarVerificationStatus() != VerificationStatus.VERIFIED) {
                // DOC_BLOCK already set during validation
                return;
            }
        }

        // Resolve appointment date
        LocalDate preferredDate = app.getPreferredDate();
        LocalDate appointmentDate = resolveAppointmentDate(app, runTs.toLocalDate(), preferredDate);
        if (appointmentDate == null) return;

        // Booking window check
        Centre centre = getCentre(app.getCentrePreference());
        if (centre == null) return;

        if (!bookingWindowValidator.isEligible(app, centre, appointmentDate, runTs.toLocalDate())) return;

        // Ensure slots exist for this date
        ensureSlotsExist(centre, appointmentDate);

        // Ensure quotas initialized
        ensureQuotasInitialized(centre, appointmentDate);

        // Consume quota
        QuotaType usedQuota = quotaManager.consumeForApplication(app, centre, appointmentDate);
        if (usedQuota == null) return;

        // Find best slot (prefer band, then earliest)
        Slot slot = findBestSlot(centre.getCentreId(), appointmentDate, app.getPreferredBand());
        if (slot == null) {
            // Return quota since slot not available
            quotaManager.returnQuota(centre.getCentreId(), appointmentDate, usedQuota);
            return;
        }

        // Allocate
        boolean isWait = slot.allocate();

        // Compute fee on appointment date
        Category cat = app.getCategory();
        FeeBreakdown fee = feeCalculator.breakdown(app, appointmentDate);
        feeBreakdowns.put(appId, fee);

        // Create appointment
        Appointment appt = new Appointment();
        appt.setAppointmentId("APT-" + appId);
        appt.setApplicationId(appId);
        appt.setApplicantId(app.getApplicantId());
        appt.setCentreId(centre.getCentreId());
        appt.setSlotId(slot.getSlotId());
        appt.setAppointmentDate(appointmentDate);
        appt.setAppointmentTime(slot.getStartTime());
        appt.setServiceType(app.getServiceType());
        appt.setCategory(cat);
        appt.setStatus(isWait ? AppointmentStatus.WAIT : AppointmentStatus.BOOKED);
        appt.setFeeAmount(fee.getTotal());
        appt.setBookedAt(runTs);
        appointments.put(appId, appt);
        appointmentHistory.computeIfAbsent(appId, k -> new ArrayList<>()).add(appt);
        appointmentQuotaPool.put(appId, usedQuota);
        appointmentPriorityReason.put(appId, cat == null ? "NORMAL" : cat.name());

        allocatedAppIds.add(appId);
        app.setApplicationStatus(ApplicationStatus.ALLOCATED);
        app.setAllocatedSlotId(slot.getSlotId());

        // Add to appointments.csv rows
        appointmentsRows.add(new String[]{
            appId,
            "ALLOCATED",
            formatDateTime(runTs),
            centre.getCentreId(),
            appointmentDate.toString(),
            slot.getStartTime().toString(),
            usedQuota.name(),
            cat == null ? "NORMAL" : cat.name()
        });
    }

    private void handleReschedule(String[] cmd) {
        // RESCHEDULE|app_id|request_ts|new_preferred_date|band
        String appId = cmd[1];
        LocalDateTime requestTs = parseDateTime(cmd[2]);
        LocalDate newDate = LocalDate.parse(cmd[3]);
        String band = cmd.length > 4 ? cmd[4] : "ANY";

        Application app = getApp(appId);
        Appointment appt = appointments.get(appId);

        if (app == null || appt == null) {
            commandLog.add("RESCHEDULE|" + appId + "|REJECTED|No active appointment");
            return;
        }
        if (appt.getStatus() == AppointmentStatus.CANCELLED ||
            appt.getStatus() == AppointmentStatus.EXPIRED) {
            commandLog.add("RESCHEDULE|" + appId + "|REJECTED|Appointment not active");
            return;
        }

        rescheduleService.recordInitialBooking(app, appt.getBookedAt());

        // Check reschedule limits
        if (!rescheduleService.canReschedule(app, requestTs)) {
            commandLog.add("RESCHEDULE|" + appId + "|REJECTED|Exceeded reschedule limit or spacing");
            return;
        }

        // Validate new date booking window
        Centre centre = getCentre(appt.getCentreId());
        if (centre == null) return;
        if (!bookingWindowValidator.isEligible(app, centre, newDate, requestTs.toLocalDate())) {
            commandLog.add("RESCHEDULE|" + appId + "|REJECTED|Booking window invalid for " + newDate);
            return;
        }

        // Release old slot
        Slot oldSlot = slots.get(appt.getSlotId());
        if (oldSlot != null) oldSlot.releaseOne();

        // Return quota from old date
        QuotaType oldQuota = appointmentQuotaPool.get(appId);
        if (oldQuota != null) {
            quotaManager.returnQuota(appt.getCentreId(), appt.getAppointmentDate(), oldQuota);
        }

        // Ensure slots and quotas exist for new date
        ensureSlotsExist(centre, newDate);
        ensureQuotasInitialized(centre, newDate);

        // Recompute category on new date
        Category newCat = allocationEngine.calculateCategory(app, newDate);
        app.setCategory(newCat);

        // Consume quota on new date
        QuotaType newQuota = quotaManager.consumeForApplication(app, centre, newDate);
        if (newQuota == null) {
            // Restore old slot
            if (oldSlot != null) oldSlot.allocate();
            if (oldQuota != null) quotaManager.consumeSpecific(appt.getCentreId(), appt.getAppointmentDate(), oldQuota);
            commandLog.add("RESCHEDULE|" + appId + "|REJECTED|No quota on new date");
            return;
        }

        // Find new slot
        Slot newSlot = findBestSlot(centre.getCentreId(), newDate, band);
        if (newSlot == null) {
            quotaManager.returnQuota(centre.getCentreId(), newDate, newQuota);
            if (oldSlot != null) oldSlot.allocate();
            if (oldQuota != null) quotaManager.consumeSpecific(appt.getCentreId(), appt.getAppointmentDate(), oldQuota);
            commandLog.add("RESCHEDULE|" + appId + "|REJECTED|No slot on new date");
            return;
        }

        boolean isWait = newSlot.allocate();

        // Update reschedule service
        rescheduleService.reschedule(app, appt, appt.getSlotId(), newSlot.getSlotId(), requestTs, "RESCHEDULE");

        // Update appointment
        String oldSlotId = appt.getSlotId();
        appt.setSlotId(newSlot.getSlotId());
        appt.setAppointmentDate(newDate);
        appt.setAppointmentTime(newSlot.getStartTime());
        appt.setStatus(isWait ? AppointmentStatus.WAIT : AppointmentStatus.RESCHEDULED);
        appt.setCategory(newCat);
        appointmentQuotaPool.put(appId, newQuota);
        appointmentPriorityReason.put(appId, newCat == null ? "NORMAL" : newCat.name());

        // Recompute fee on new date
        FeeBreakdown fee = feeCalculator.breakdown(app, newDate);
        feeBreakdowns.put(appId, fee);
        appt.setFeeAmount(fee.getTotal());

        // Record in appointments.csv
        appointmentsRows.add(new String[]{
            appId,
            "RESCHEDULED",
            formatDateTime(requestTs),
            centre.getCentreId(),
            newDate.toString(),
            newSlot.getStartTime().toString(),
            newQuota.name(),
            newCat == null ? "NORMAL" : newCat.name()
        });

        commandLog.add("RESCHEDULE|" + appId + "|OK|" + oldSlotId + " -> " + newSlot.getSlotId());
    }

    private void handleCancel(String[] cmd) {
        // CANCEL|app_id|request_ts
        String appId = cmd[1];
        LocalDateTime requestTs = parseDateTime(cmd[2]);

        Application app = getApp(appId);
        Appointment appt = appointments.get(appId);

        if (appt == null || appt.getStatus() == AppointmentStatus.CANCELLED) {
            commandLog.add("CANCEL|" + appId + "|REJECTED|No active appointment or already cancelled");
            return;
        }

        BigDecimal fee = appt.getFeeAmount() != null ? appt.getFeeAmount() :
                (feeBreakdowns.containsKey(appId) ? feeBreakdowns.get(appId).getTotal() : BigDecimal.ZERO);
        boolean isTatkal = appt.getServiceType() == ServiceType.TATKAL ||
                           appt.getCategory() == Category.TATKAL;

        LocalDateTime apptDateTime = appt.getAppointmentDate().atTime(
                appt.getAppointmentTime() != null ? appt.getAppointmentTime() : LocalTime.of(9, 0));

        // Calculate refund
        BigDecimal baseFee = feeBreakdowns.containsKey(appId) ? feeBreakdowns.get(appId).getBaseFee() : fee;
        BigDecimal tatkalSurcharge = feeBreakdowns.containsKey(appId) ? feeBreakdowns.get(appId).getTatkalSurcharge() : BigDecimal.ZERO;

        long hours = Duration.between(requestTs, apptDateTime).toHours();
        BigDecimal refund;
        boolean forfeited = false;
        if (hours > 48) {
            refund = baseFee.subtract(new BigDecimal("100.00"));
            if (refund.compareTo(BigDecimal.ZERO) < 0) refund = BigDecimal.ZERO;
        } else if (hours >= 24) {
            // Exactly 48h falls here (EC-07)
            refund = baseFee.multiply(new BigDecimal("0.50"));
            refund = MoneyUtils.scale(refund);
        } else {
            refund = BigDecimal.ZERO;
            forfeited = true;
        }
        // Tatkal surcharge never refunded
        // refund is on base fee only

        refundAmounts.put(appId, refund);
        forfeitedFees.put(appId, forfeited);

        // Release slot
        Slot slot = slots.get(appt.getSlotId());
        if (slot != null) slot.releaseOne();

        // Return quota
        QuotaType quota = appointmentQuotaPool.get(appId);
        if (quota != null) {
            quotaManager.returnQuota(appt.getCentreId(), appt.getAppointmentDate(), quota);
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        allocatedAppIds.remove(appId);
        if (app != null) {
            app.setApplicationStatus(ApplicationStatus.SUBMITTED); // allow rebook
            app.setEligibleForAllocation(true);
        }
        appointments.remove(appId);

        appointmentsRows.add(new String[]{
            appId,
            "CANCELLED",
            formatDateTime(requestTs),
            appt.getCentreId(),
            appt.getAppointmentDate().toString(),
            appt.getAppointmentTime() != null ? appt.getAppointmentTime().toString() : "09:00",
            appointmentQuotaPool.getOrDefault(appId, QuotaType.NORMAL).name(),
            appointmentPriorityReason.getOrDefault(appId, "NORMAL")
        });

        commandLog.add("CANCEL|" + appId + "|OK|refund=" + refund + "|forfeited=" + forfeited);
    }

    private void handleNoShow(String[] cmd) {
        // NO_SHOW|app_id|appointment_date
        String appId = cmd[1];
        LocalDate apptDate = LocalDate.parse(cmd[2]);

        Application app = getApp(appId);
        Appointment appt = appointments.get(appId);

        if (appt == null) {
            commandLog.add("NO_SHOW|" + appId + "|REJECTED|No active appointment");
            return;
        }

        // Record no-show
        appt.setStatus(AppointmentStatus.EXPIRED);
        allocatedAppIds.remove(appId);

        int nsCount = noShowCounts.merge(appId, 1, Integer::sum);
        lastNoShowDate.put(appId, apptDate);

        if (nsCount >= 2 && app != null) {
            // Second no-show: block permanently
            app.setApplicationStatus(ApplicationStatus.REJECTED);
            app.setEligibleForAllocation(false);
            app.setBlockingReason("Second no-show: fee forfeited, fresh application required");
            forfeitedFees.put(appId, true);
            commandLog.add("NO_SHOW|" + appId + "|BLOCKED|Second no-show");
        } else {
            // First no-show: allow rebook
            if (app != null) {
                app.setApplicationStatus(ApplicationStatus.SUBMITTED);
                app.setEligibleForAllocation(true); // allow rebook
            }
            commandLog.add("NO_SHOW|" + appId + "|EXPIRED|First no-show, rebook allowed within 30 days");
        }

        // Keep appointment in history but remove from active
        appointments.remove(appId);

        appointmentsRows.add(new String[]{
            appId,
            "NO_SHOW",
            apptDate.atTime(9, 0).toString(),
            appt.getCentreId(),
            appt.getAppointmentDate().toString(),
            appt.getAppointmentTime() != null ? appt.getAppointmentTime().toString() : "09:00",
            appointmentQuotaPool.getOrDefault(appId, QuotaType.NORMAL).name(),
            appointmentPriorityReason.getOrDefault(appId, "NORMAL")
        });
    }

    private void handleRebook(String[] cmd) {
        // REBOOK|app_id|request_ts|preferred_date|band
        String appId = cmd[1];
        LocalDateTime requestTs = parseDateTime(cmd[2]);
        LocalDate preferredDate = LocalDate.parse(cmd[3]);
        String band = cmd.length > 4 ? cmd[4] : "ANY";

        Application app = getApp(appId);
        if (app == null) {
            commandLog.add("REBOOK|" + appId + "|REJECTED|Application not found");
            return;
        }

        // Cannot rebook if currently has active appointment
        if (appointments.containsKey(appId)) {
            commandLog.add("REBOOK|" + appId + "|REJECTED|Active appointment already exists");
            appointmentsRows.add(new String[]{
                appId, "REBOOK_REJECTED", formatDateTime(requestTs), "", preferredDate.toString(), "", "", "ACTIVE_APPOINTMENT_EXISTS"
            });
            return;
        }

        // Check if second no-show
        if (!app.isEligibleForAllocation()) {
            commandLog.add("REBOOK|" + appId + "|REJECTED|Second no-show - fresh application required");
            return;
        }

        // Free rebook within 30 days of first no-show
        LocalDate noShowDate = lastNoShowDate.get(appId);
        if (noShowDate != null) {
            long daysSince = Duration.between(noShowDate.atStartOfDay(), requestTs).toDays();
            if (daysSince > 30) {
                commandLog.add("REBOOK|" + appId + "|REJECTED|Rebook period expired (>30 days since no-show)");
                return;
            }
        }

        // Resolve centre
        Centre centre = getCentre(app.getCentrePreference());
        if (centre == null) return;

        // Validate booking window
        if (!bookingWindowValidator.isEligible(app, centre, preferredDate, requestTs.toLocalDate())) {
            commandLog.add("REBOOK|" + appId + "|REJECTED|Booking window invalid for " + preferredDate);
            return;
        }

        // Recompute category on preferred date
        Category cat = allocationEngine.calculateCategory(app, preferredDate);
        app.setCategory(cat);

        // Ensure slots and quotas
        ensureSlotsExist(centre, preferredDate);
        ensureQuotasInitialized(centre, preferredDate);

        // Consume quota
        QuotaType quota = quotaManager.consumeForApplication(app, centre, preferredDate);
        if (quota == null) {
            commandLog.add("REBOOK|" + appId + "|REJECTED|No quota available");
            return;
        }

        // Find slot
        Slot slot = findBestSlot(centre.getCentreId(), preferredDate, band);
        if (slot == null) {
            quotaManager.returnQuota(centre.getCentreId(), preferredDate, quota);
            commandLog.add("REBOOK|" + appId + "|REJECTED|No slot available");
            return;
        }

        boolean isWait = slot.allocate();

        // Compute fee
        FeeBreakdown fee = feeCalculator.breakdown(app, preferredDate);
        feeBreakdowns.put(appId, fee);

        // Create new appointment
        Appointment appt = new Appointment();
        appt.setAppointmentId("APT-" + appId + "-R" + noShowCounts.getOrDefault(appId, 1));
        appt.setApplicationId(appId);
        appt.setApplicantId(app.getApplicantId());
        appt.setCentreId(centre.getCentreId());
        appt.setSlotId(slot.getSlotId());
        appt.setAppointmentDate(preferredDate);
        appt.setAppointmentTime(slot.getStartTime());
        appt.setServiceType(app.getServiceType());
        appt.setCategory(cat);
        appt.setStatus(isWait ? AppointmentStatus.WAIT : AppointmentStatus.BOOKED);
        appt.setFeeAmount(fee.getTotal());
        appt.setBookedAt(requestTs);
        appointments.put(appId, appt);
        appointmentHistory.computeIfAbsent(appId, k -> new ArrayList<>()).add(appt);
        appointmentQuotaPool.put(appId, quota);
        appointmentPriorityReason.put(appId, cat == null ? "NORMAL" : cat.name());

        allocatedAppIds.add(appId);
        app.setApplicationStatus(ApplicationStatus.ALLOCATED);

        appointmentsRows.add(new String[]{
            appId,
            "REBOOKED",
            formatDateTime(requestTs),
            centre.getCentreId(),
            preferredDate.toString(),
            slot.getStartTime().toString(),
            quota.name(),
            cat == null ? "NORMAL" : cat.name()
        });

        commandLog.add("REBOOK|" + appId + "|OK|" + slot.getSlotId());
    }

    private void handleWalkIn(String[] cmd) {
        // WALK_IN|app_id|centre_id|arrival_ts
        String appId = cmd[1];
        String centreId = cmd[2];
        LocalDateTime arrivalTs = parseDateTime(cmd[3]);
        LocalDate arrivalDate = arrivalTs.toLocalDate();

        Application app = getApp(appId);
        if (app == null) {
            commandLog.add("WALK_IN|" + appId + "|REFUSED|Application not found");
            return;
        }

        DocumentValidationResult validation = validateApplication(app, arrivalDate);
        if (validation.getValidationStatus() != ValidationStatus.VALID) {
            String reason = String.join("; ", validation.getBlockingGaps());
            commandLog.add("WALK_IN|" + appId + "|REFUSED|Document validation failed: " + reason);
            appointmentsRows.add(new String[]{
                appId, "WALK_IN_REFUSED", formatDateTime(arrivalTs), centreId,
                arrivalDate.toString(), "", "NORMAL", "DOC_BLOCK"
            });
            daySheetRows.add(new String[]{
                centreId, arrivalDate.toString(), "", appId, "NORMAL", "WALK_IN_REFUSED", "false"
            });
            return;
        }

        // Only SENIOR_CITIZEN or DIFFERENTLY_ABLED
        Category cat = allocationEngine.calculateCategory(app, arrivalDate);
        app.setCategory(cat);
        if (cat != Category.SENIOR_CITIZEN && cat != Category.DIFFERENTLY_ABLED) {
            commandLog.add("WALK_IN|" + appId + "|REFUSED|Not eligible (must be SENIOR_CITIZEN or DIFFERENTLY_ABLED)");
            appointmentsRows.add(new String[]{
                appId, "WALK_IN_REFUSED", formatDateTime(arrivalTs), centreId,
                arrivalDate.toString(), "", "NORMAL", cat == null ? "NORMAL" : cat.name()
            });
            return;
        }

        // Check daily walk-in cap per centre per date
        // Cap = floor(day_capacity * 10%) per centre per date
        Centre centre = getCentre(centreId);
        if (centre == null) {
            commandLog.add("WALK_IN|" + appId + "|REFUSED|Centre not found: " + centreId);
            return;
        }

        int dayCap = computeDayCapacity(centreId, arrivalDate);
        int walkInCap = Math.floorDiv(dayCap * 10, 100);
        String capKey = centreId + "|" + arrivalDate;
        int currentCount = walkInCounts.getOrDefault(capKey, 0);

        if (currentCount >= walkInCap) {
            commandLog.add("WALK_IN|" + appId + "|REFUSED|Walk-in cap (" + walkInCap + ") reached for " + centreId + " on " + arrivalDate);
            appointmentsRows.add(new String[]{
                appId, "WALK_IN_REFUSED", formatDateTime(arrivalTs), centreId,
                arrivalDate.toString(), "", "NORMAL", cat.name()
            });
            daySheetRows.add(new String[]{
                centreId, arrivalDate.toString(), "", appId, cat.name(), "WALK_IN_REFUSED", "false"
            });
            return;
        }

        // Find a vacant slot (from no-shows)
        ensureSlotsExist(centre, arrivalDate);
        Slot slot = findVacantSlotForWalkIn(centreId, arrivalDate);
        if (slot == null) {
            commandLog.add("WALK_IN|" + appId + "|REFUSED|No vacant slot");
            appointmentsRows.add(new String[]{
                appId, "WALK_IN_REFUSED", formatDateTime(arrivalTs), centreId,
                arrivalDate.toString(), "", "NORMAL", cat.name()
            });
            return;
        }

        slot.allocate();
        walkInCounts.put(capKey, currentCount + 1);

        FeeBreakdown fee = feeCalculator.breakdown(app, arrivalDate);
        feeBreakdowns.put(appId, fee);

        Appointment appt = new Appointment();
        appt.setAppointmentId("APT-" + appId + "-WI");
        appt.setApplicationId(appId);
        appt.setApplicantId(app.getApplicantId());
        appt.setCentreId(centreId);
        appt.setSlotId(slot.getSlotId());
        appt.setAppointmentDate(arrivalDate);
        appt.setAppointmentTime(slot.getStartTime());
        appt.setServiceType(app.getServiceType());
        appt.setCategory(cat);
        appt.setStatus(AppointmentStatus.BOOKED);
        appt.setFeeAmount(fee.getTotal());
        appt.setBookedAt(arrivalTs);
        appointments.put(appId, appt);
        appointmentHistory.computeIfAbsent(appId, k -> new ArrayList<>()).add(appt);
        appointmentQuotaPool.put(appId, QuotaType.NORMAL);
        appointmentPriorityReason.put(appId, "WALK_IN");

        allocatedAppIds.add(appId);
        app.setApplicationStatus(ApplicationStatus.ALLOCATED);

        appointmentsRows.add(new String[]{
            appId, "WALK_IN_SERVED", formatDateTime(arrivalTs), centreId,
            arrivalDate.toString(), slot.getStartTime().toString(),
            "NORMAL", cat.name()
        });

        commandLog.add("WALK_IN|" + appId + "|OK|" + slot.getSlotId() + " cap=" + walkInCap + " count=" + (currentCount + 1));
    }

    private void handlePrintApplication(String[] cmd) {
        // PRINT_APPLICATION|app_id
        // contributes to validation.csv and fees.csv (already tracked)
        String appId = cmd[1];
        commandLog.add("PRINT_APPLICATION|" + appId + "|OK");
    }

    private void handlePrintDaySheet(String[] cmd) {
        // PRINT_DAY_SHEET|centre_id|date
        String centreId = cmd[1];
        LocalDate date = LocalDate.parse(cmd[2]);

        // Build day sheet from current appointment state
        List<Appointment> dayAppts = new ArrayList<>();
        for (Appointment a : appointments.values()) {
            if (centreId.equals(a.getCentreId()) && date.equals(a.getAppointmentDate())) {
                dayAppts.add(a);
            }
        }
        // Also include history for this date
        for (List<Appointment> hist : appointmentHistory.values()) {
            for (Appointment a : hist) {
                if (centreId.equals(a.getCentreId()) && date.equals(a.getAppointmentDate())
                        && !dayAppts.contains(a)) {
                    dayAppts.add(a);
                }
            }
        }

        // Sort: by slot_time, then priority (walk-ins last in their slot)
        dayAppts.sort((a, b) -> {
            int t = Comparator.comparing(Appointment::getAppointmentTime,
                    Comparator.nullsLast(Comparator.naturalOrder())).compare(a, b);
            if (t != 0) return t;
            // Walk-ins last
            boolean aWalkIn = "WALK_IN".equals(appointmentPriorityReason.getOrDefault(a.getApplicationId(), ""));
            boolean bWalkIn = "WALK_IN".equals(appointmentPriorityReason.getOrDefault(b.getApplicationId(), ""));
            if (aWalkIn != bWalkIn) return aWalkIn ? 1 : -1;
            return 0;
        });

        for (Appointment a : dayAppts) {
            String status = a.getStatus() != null ? a.getStatus().name() : "ALLOCATED";
            if (a.getStatus() == AppointmentStatus.BOOKED) {
                status = "ALLOCATED";
            }
            if ("WALK_IN".equals(appointmentPriorityReason.getOrDefault(a.getApplicationId(), ""))) {
                status = "WALK_IN_SERVED";
            }
            daySheetRows.add(new String[]{
                centreId,
                date.toString(),
                a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "",
                a.getApplicationId(),
                a.getCategory() != null ? a.getCategory().name() : "NORMAL",
                status,
                String.valueOf(a.getStatus() == AppointmentStatus.WAIT)
            });
        }

        commandLog.add("PRINT_DAY_SHEET|" + centreId + "|" + date + "|rows=" + dayAppts.size());
    }

    // ==================== QUOTA RELEASE ====================

    private void triggerQuotaReleasesUpTo(LocalDateTime upToTime) {
        // For each known appointment date, check if T-2 working days at 17:00 has passed
        // Collect all known appointment dates from quotas
        Set<String> dateKeys = new HashSet<>();
        for (QuotaAllocation qa : quotaManager.getAll()) {
            if (qa.getAppointmentDate() != null) {
                String k = qa.getCentreId() + "|" + qa.getAppointmentDate().toLocalDate();
                dateKeys.add(k);
            }
        }

        for (String key : dateKeys) {
            String[] parts = key.split("\\|");
            String centreId = parts[0];
            LocalDate apptDate = LocalDate.parse(parts[1]);

            String releaseKey = centreId + "|" + apptDate;
            if (releasedDates.contains(releaseKey)) continue;

            // Release time = apptDate - 2 working days at 17:00
            LocalDate releaseDate = subtractWorkingDays(apptDate, 2);
            LocalDateTime releaseTime = releaseDate.atTime(17, 0);

            if (!upToTime.isBefore(releaseTime)) {
                // Release non-TATKAL quotas for this centre+date
                quotaManager.releaseAllEligible(centreId, apptDate);
                releasedDates.add(releaseKey);
            }
        }
    }

    private LocalDate subtractWorkingDays(LocalDate date, int days) {
        LocalDate result = date;
        int count = 0;
        while (count < days) {
            result = result.minusDays(1);
            if (isWorkingDay(result)) count++;
        }
        return result;
    }

    private boolean isWorkingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !holidays.contains(date);
    }

    // ==================== SLOT MANAGEMENT ====================

    private void ensureSlotsExist(Centre centre, LocalDate date) {
        // Check if we already have slots for this centre+date
        String prefix = centre.getCentreId() + "|" + date + "|";
        boolean exists = slots.keySet().stream().anyMatch(k -> k.startsWith(prefix));
        if (exists) return;

        // Generate from slot_capacity.csv data
        LocalTime time = LocalTime.of(9, 0);
        LocalTime lastSlot = LocalTime.of(15, 45);

        while (!time.isAfter(lastSlot)) {
            String slotKey = centre.getCentreId() + "|" + date + "|" + time;
            int cap = slotCapacityMap.getOrDefault(slotKey, 0);
            if (cap > 0) {
                String slotId = centre.getCentreId() + "-" + date + "-" + time;
                Slot slot = new Slot(slotId, centre.getCentreId(), date, time, cap);
                slots.put(slotId, slot);
            }
            time = time.plusMinutes(15);
        }
    }

    private void ensureQuotasInitialized(Centre centre, LocalDate date) {
        // Compute day capacity from slot_capacity.csv
        int dayCapacity = computeDayCapacity(centre.getCentreId(), date);
        if (dayCapacity == 0) dayCapacity = centre.getDailyCapacity();

        // Temporarily set centre capacity for quota init
        int original = centre.getDailyCapacity();
        centre.setDailyCapacity(dayCapacity);

        if (quotaManager.getQuota(centre.getCentreId(), date, QuotaType.NORMAL) == null) {
            quotaManager.initializeQuotas(centre, date);
        }
        centre.setDailyCapacity(original);
    }

    private int computeDayCapacity(String centreId, LocalDate date) {
        int total = 0;
        LocalTime time = LocalTime.of(9, 0);
        LocalTime lastSlot = LocalTime.of(15, 45);
        while (!time.isAfter(lastSlot)) {
            String key = centreId + "|" + date + "|" + time;
            total += slotCapacityMap.getOrDefault(key, 0);
            time = time.plusMinutes(15);
        }
        return total;
    }

    private Slot findBestSlot(String centreId, LocalDate date, String band) {
        String prefix = centreId + "-" + date + "-";
        List<Slot> available = slots.values().stream()
                .filter(s -> s.getCentreId().equals(centreId)
                        && s.getDate().equals(date)
                        && s.canAllocate())
                .sorted(Comparator.comparing(Slot::getStartTime))
                .collect(Collectors.toList());

        if (available.isEmpty()) return null;

        // Apply band preference
        if (band != null && !"ANY".equalsIgnoreCase(band) && !"-".equals(band)) {
            LocalTime bandStart, bandEnd;
            if ("MORNING".equalsIgnoreCase(band)) {
                bandStart = LocalTime.of(9, 0); bandEnd = LocalTime.of(11, 45);
            } else { // AFTERNOON
                bandStart = LocalTime.of(12, 0); bandEnd = LocalTime.of(15, 45);
            }
            final LocalTime bs = bandStart, be = bandEnd;
            Slot preferred = available.stream()
                    .filter(s -> !s.getStartTime().isBefore(bs) && !s.getStartTime().isAfter(be))
                    .findFirst().orElse(null);
            if (preferred != null) return preferred;
        }
        return available.get(0);
    }

    private Slot findVacantSlotForWalkIn(String centreId, LocalDate date) {
        // Vacant = slots that had scheduled applicants who are no-shows (capacity was released)
        return slots.values().stream()
                .filter(s -> s.getCentreId().equals(centreId)
                        && s.getDate().equals(date)
                        && s.canAllocate())
                .sorted(Comparator.comparing(Slot::getStartTime))
                .findFirst().orElse(null);
    }

    // ==================== VALIDATION ====================

    private void preValidateAll() {
        // Determine appointment dates per application preference
        for (Application app : applications) {
            if (app.getChannel() == Channel.WALK_IN) {
                // Walk-in applicants skip normal validation for allocation
                app.setEligibleForAllocation(false); // won't be batch-allocated
                continue;
            }

            // Use preferred_date as the target appointment date for expiry checks
            LocalDate apptDate = app.getPreferredDate() != null ? app.getPreferredDate() : LocalDate.of(2026, 10, 5);

            validateApplication(app, apptDate);

            // Also compute initial fee
            if (app.getValidationStatus() == ValidationStatus.VALID) {
                FeeBreakdown fee = feeCalculator.breakdown(app, apptDate);
                feeBreakdowns.put(app.getApplicationId(), fee);
            }
        }
    }

    private DocumentValidationResult validateApplication(Application app, LocalDate appointmentDate) {
        List<Document> appDocuments = documents.stream()
                .filter(document -> app.getApplicationId().equals(document.getApplicationId()))
                .toList();
        DocumentValidationResult result = documentValidator.validate(app, appDocuments, appointmentDate);
        validationResults.put(app.getApplicationId(), result);
        return result;
    }

    private LocalDate resolveAppointmentDate(Application app, LocalDate asOf, LocalDate preferred) {
        LocalDate earliest = DateUtils.nextWorkingDay(asOf, holidays);
        if (preferred != null && !preferred.isBefore(earliest)) {
            if (isWorkingDay(preferred)) return preferred;
        }
        return earliest;
    }

    // ==================== DATA LOADING ====================

    private void loadHolidays(Path path) throws Exception {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(path);
        if (rows.size() <= 1) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length >= 1 && !row[0].isBlank()) {
                try {
                    holidays.add(LocalDate.parse(row[0].trim()));
                } catch (Exception ignored) {}
            }
        }
    }

    private void loadCentres(Path path) throws Exception {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(path);
        if (rows.size() <= 1) return;
        String[] header = rows.get(0);
        int idIdx = idx(header, "centre_id");
        int nameIdx = idx(header, "centre_name");
        int cityIdx = idx(header, "city");
        int firstSlotIdx = idx(header, "first_slot");
        int lastSlotIdx = idx(header, "last_slot");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0) continue;
            Centre c = new Centre();
            c.setCentreId(safeGet(row, idIdx));
            c.setName(safeGet(row, nameIdx));
            c.setCity(safeGet(row, cityIdx));
            c.setOpenTime(safeGet(row, firstSlotIdx).isBlank() ? LocalTime.of(9, 0) : LocalTime.parse(safeGet(row, firstSlotIdx)));
            c.setCloseTime(safeGet(row, lastSlotIdx).isBlank() ? LocalTime.of(15, 45) : LocalTime.parse(safeGet(row, lastSlotIdx)));
            c.setDailyCapacity(28); // default, will be computed from slot_capacity
            centres.add(c);
        }
    }

    private void loadApplications(Path path) throws Exception {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(path);
        if (rows.size() <= 1) return;
        String[] header = rows.get(0);

        int appIdIdx = idx(header, "app_id");
        int nameIdx = idx(header, "applicant_name");
        int genderIdx = idx(header, "gender");
        int dobIdx = idx(header, "dob");
        int mobileIdx = idx(header, "mobile");
        int serviceIdx = idx(header, "service_type");
        int bookletIdx = idx(header, "booklet_pages");
        int centreIdx = idx(header, "centre_id");
        int prefDateIdx = idx(header, "preferred_date");
        int prefBandIdx = idx(header, "preferred_band");
        int tsIdx = idx(header, "application_ts");
        int channelIdx = idx(header, "channel");
        int diffAbledIdx = idx(header, "differently_abled");
        int aadhaarIdx = idx(header, "aadhaar_verified");
        int addrChangeIdx = idx(header, "address_changed");
        int oldPassExpIdx = idx(header, "old_passport_expiry");
        int lostDamagedIdx = idx(header, "lost_or_damaged");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0) continue;
            try {
                Application app = new Application();
                String appId = safeGet(row, appIdIdx);
                app.setApplicationId(appId);
                app.setApplicantId(appId); // same as app_id in this dataset
                app.setFullName(safeGet(row, nameIdx));

                String dobStr = safeGet(row, dobIdx);
                if (!dobStr.isBlank()) app.setDateOfBirth(LocalDate.parse(dobStr));

                String tsStr = safeGet(row, tsIdx);
                if (!tsStr.isBlank()) app.setApplicationTimestamp(parseDateTime(tsStr));

                String serviceStr = safeGet(row, serviceIdx).replace("-", "_").toUpperCase();
                try { app.setServiceType(ServiceType.valueOf(serviceStr)); }
                catch (Exception e) { app.setServiceType(ServiceType.NORMAL); }
                if (app.getServiceType() == ServiceType.TATKAL) {
                    app.addCategory(Category.TATKAL);
                    app.setCategory(Category.TATKAL);
                }

                String bookletStr = safeGet(row, bookletIdx).replaceAll("[^0-9]", "");
                app.setBookletSize("60".equals(bookletStr) ? BookletSize.PAGES_60 : BookletSize.PAGES_36);

                app.setCentrePreference(safeGet(row, centreIdx));

                String prefDateStr = safeGet(row, prefDateIdx);
                if (!prefDateStr.isBlank()) app.setPreferredDate(LocalDate.parse(prefDateStr));

                app.setPreferredBand(safeGet(row, prefBandIdx));

                // Channel
                String channelStr = safeGet(row, channelIdx).toUpperCase();
                try { app.setChannel(Channel.valueOf(channelStr)); }
                catch (Exception e) { app.setChannel(Channel.ONLINE); }

                // Differently abled
                if ("true".equalsIgnoreCase(safeGet(row, diffAbledIdx))) {
                    app.addCategory(Category.DIFFERENTLY_ABLED);
                }

                // Aadhaar verified
                String av = safeGet(row, aadhaarIdx);
                app.setAadhaarVerificationStatus("true".equalsIgnoreCase(av) ? VerificationStatus.VERIFIED : VerificationStatus.UNVERIFIED);

                // Address changed
                app.setAddressChangeFlag("true".equalsIgnoreCase(safeGet(row, addrChangeIdx)));

                // Old passport expiry
                String oldExpStr = safeGet(row, oldPassExpIdx);
                if (!oldExpStr.isBlank()) app.setOldPassportExpiry(LocalDate.parse(oldExpStr));

                // Lost or damaged
                boolean lostDamaged = "true".equalsIgnoreCase(safeGet(row, lostDamagedIdx));
                app.setPassportCondition(lostDamaged ? PassportCondition.LOST : PassportCondition.NORMAL);

                applications.add(app);
            } catch (Exception e) {
                // skip invalid row
            }
        }
    }

    private void loadDocuments(Path path) throws Exception {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(path);
        if (rows.size() <= 1) return;
        String[] header = rows.get(0);

        int docIdIdx = idx(header, "doc_id");
        int appIdIdx = idx(header, "app_id");
        int typeIdx = idx(header, "doc_type");
        int verIdx = idx(header, "verification");
        int validUntilIdx = idx(header, "valid_until");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0) continue;
            try {
                Document doc = new Document();
                doc.setDocumentId(safeGet(row, docIdIdx));
                doc.setApplicationId(safeGet(row, appIdIdx));

                String typeStr = safeGet(row, typeIdx).toUpperCase().trim();
                DocumentType docType = parseDocType(typeStr);
                if (docType == null) continue; // reject unknown types
                doc.setDocumentType(docType);

                String verStr = safeGet(row, verIdx).toUpperCase().trim();
                try { doc.setVerificationStatus(VerificationStatus.valueOf(verStr)); }
                catch (Exception e) { doc.setVerificationStatus(VerificationStatus.UNVERIFIED); }

                String validUntilStr = safeGet(row, validUntilIdx).trim();
                if (!validUntilStr.isBlank() && !"-".equals(validUntilStr)) {
                    doc.setExpiryDate(LocalDate.parse(validUntilStr));
                }
                documents.add(doc);
            } catch (Exception ignored) {}
        }
    }

    private void loadSlotCapacity(Path path) throws Exception {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(path);
        if (rows.size() <= 1) return;
        String[] header = rows.get(0);

        int centreIdx = idx(header, "centre_id");
        int dateIdx = idx(header, "slot_date");
        int timeIdx = idx(header, "slot_time");
        int capIdx = idx(header, "capacity");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0) continue;
            try {
                String centreId = safeGet(row, centreIdx);
                String dateStr = safeGet(row, dateIdx);
                String timeStr = safeGet(row, timeIdx);
                String capStr = safeGet(row, capIdx);
                if (centreId.isBlank() || dateStr.isBlank() || timeStr.isBlank() || capStr.isBlank()) continue;

                // Store with key centreId|date|time
                String key = centreId + "|" + dateStr + "|" + timeStr;
                slotCapacityMap.put(key, Integer.parseInt(capStr));
            } catch (Exception ignored) {}
        }
    }

    // ==================== CSV OUTPUT ====================

    private void writeValidationCsv(Path dir) throws Exception {
        CsvWriter writer = new CsvWriter();
        List<String[]> rows = new ArrayList<>();
        for (Application app : applications) {
            DocumentValidationResult vr = validationResults.get(app.getApplicationId());
            String category = app.getCategory() != null ? app.getCategory().name() :
                    (allocationEngine.calculateCategory(app, app.getPreferredDate() != null ? app.getPreferredDate() : LocalDate.now()).name());
            int score = vr != null ? vr.getDocumentScore() : (app.getDocumentScore() != null ? app.getDocumentScore() : 0);
            String verMode = vr != null && vr.getVerificationMode() != null ? vr.getVerificationMode().name() :
                    (app.getVerificationMode() != null ? app.getVerificationMode().name() : "");
            // Map NOT_REQUIRED -> SKIPPED per spec
            if ("NOT_REQUIRED".equals(verMode)) verMode = "SKIPPED";
            // The output schema admits only POST/PRE/SKIPPED. Score < 3 remains
            // DOC_BLOCK in validation status, but is reported as PRE on the verification track.
            if ("DOC_BLOCK".equals(verMode)) verMode = "PRE";
            String status = vr != null && vr.getValidationStatus() != null ? vr.getValidationStatus().name() :
                    (app.getValidationStatus() != null ? app.getValidationStatus().name() : "DOC_BLOCK");
            String gaps = "";
            if (vr != null && vr.getBlockingGaps() != null && !vr.getBlockingGaps().isEmpty()) {
                gaps = String.join(";", vr.getBlockingGaps());
            } else if (app.getBlockingReason() != null) {
                gaps = app.getBlockingReason();
            }
            rows.add(new String[]{app.getApplicationId(), category, String.valueOf(score), verMode, status, gaps});
        }
        writer.write(dir.resolve("validation.csv"),
                new String[]{"app_id","category","doc_score","verification_mode","status","blocking_gaps"},
                rows);
    }

    private void writeAppointmentsCsv(Path dir) throws Exception {
        CsvWriter writer = new CsvWriter();
        writer.write(dir.resolve("appointments.csv"),
                new String[]{"app_id","action","action_ts","centre_id","slot_date","slot_time","quota_pool","priority_reason"},
                appointmentsRows);
    }

    private void writeFeesCsv(Path dir) throws Exception {
        CsvWriter writer = new CsvWriter();
        List<String[]> rows = new ArrayList<>();
        for (Application app : applications) {
            String appId = app.getApplicationId();
            FeeBreakdown fb = feeBreakdowns.get(appId);
            if (fb == null) {
                LocalDate apptDate = app.getPreferredDate() != null ? app.getPreferredDate() : LocalDate.of(2026, 10, 5);
                fb = feeCalculator.breakdown(app, apptDate);
            }
            BigDecimal refund = refundAmounts.getOrDefault(appId, BigDecimal.ZERO);
            boolean forfeited = forfeitedFees.getOrDefault(appId, false);
            rows.add(new String[]{
                appId,
                fb.getBaseFee().toPlainString(),
                fb.getSeniorDiscount().toPlainString(),
                fb.getTatkalSurcharge().toPlainString(),
                fb.getTotal().toPlainString(),
                refund.toPlainString(),
                String.valueOf(forfeited)
            });
        }
        writer.write(dir.resolve("fees.csv"),
                new String[]{"app_id","base_fee","senior_discount","tatkal_surcharge","total_fee","refund","forfeited"},
                rows);
    }

    private void writeDaySheetsCsv(Path dir) throws Exception {
        CsvWriter writer = new CsvWriter();
        writer.write(dir.resolve("day_sheets.csv"),
                new String[]{"centre_id","slot_date","slot_time","app_id","category","status","wait_flag"},
                daySheetRows);
    }

    private void writeCommandLog(Path dir) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Command Stream Execution Log\n");
        sb.append("Total commands parsed: ").append(commandCount).append("\n");
        sb.append("Command outcomes recorded: ").append(commandLog.size()).append("\n");
        sb.append("Rejected/refused/blocked/errors/skipped: ")
                .append(commandLog.stream().filter(this::isUnsuccessfulCommand).count()).append("\n\n");
        for (String line : commandLog) {
            sb.append(line).append("\n");
        }
        Files.writeString(dir.resolve("command-log.txt"), sb.toString());
    }

    // ==================== HELPERS ====================

    private List<String[]> parseCommandFile(Path path) throws Exception {
        List<String[]> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length == 0) continue;
                result.add(parts);
            }
        }
        return result;
    }

    private LocalDateTime extractTimestamp(String[] cmd) {
        if (cmd == null || cmd.length < 2) return null;
        switch (cmd[0]) {
            case "RUN_ALLOCATION": return parseDateTime(cmd[1]);
            case "RESCHEDULE":     return cmd.length > 2 ? parseDateTime(cmd[2]) : null;
            case "CANCEL":         return cmd.length > 2 ? parseDateTime(cmd[2]) : null;
            case "NO_SHOW":        return cmd.length > 2 ? LocalDate.parse(cmd[2]).atTime(9, 0) : null;
            case "REBOOK":         return cmd.length > 2 ? parseDateTime(cmd[2]) : null;
            case "WALK_IN":        return cmd.length > 3 ? parseDateTime(cmd[3]) : null;
            default: return null;
        }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return simulationClock;
        s = s.trim().replace(" ", "T");
        try { return LocalDateTime.parse(s); }
        catch (Exception e) {
            try { return LocalDate.parse(s).atStartOfDay(); }
            catch (Exception e2) { return simulationClock; }
        }
    }

    private DocumentType parseDocType(String typeStr) {
        try {
            return DocumentType.valueOf(typeStr);
        } catch (Exception e) {
            // Map known aliases
            return switch (typeStr) {
                case "PHOTO" -> DocumentType.PHOTO;
                default -> null; // reject unknown
            };
        }
    }

    private int priorityRank(Category cat) {
        if (cat == null) return 50;
        return switch (cat) {
            case TATKAL -> 1;
            case SENIOR_CITIZEN -> 2;
            case MINOR -> 3;
            case DIFFERENTLY_ABLED -> 4;
            case NORMAL -> 5;
        };
    }

    private Centre getCentre(String centreId) {
        if (centreId == null) return null;
        return centres.stream().filter(c -> centreId.equals(c.getCentreId())).findFirst().orElse(null);
    }

    private Application getApp(String appId) {
        if (appId == null) return null;
        return applications.stream().filter(a -> appId.equals(a.getApplicationId())).findFirst().orElse(null);
    }

    private int idx(String[] header, String... names) {
        for (int i = 0; i < header.length; i++) {
            String col = header[i].trim().toLowerCase();
            for (String name : names) {
                if (col.equalsIgnoreCase(name)) return i;
            }
        }
        return -1;
    }

    private String safeGet(String[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length || row[idx] == null) return "";
        return row[idx].trim();
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.toString().replace("T", " ").substring(0, Math.min(19, dt.toString().replace("T", " ").length()));
    }

    public int getCommandCount() { return commandCount; }
}
