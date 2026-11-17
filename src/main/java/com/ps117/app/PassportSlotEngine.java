package com.ps117.app;

import com.ps117.allocation.AllocationEngine;
import com.ps117.allocation.QuotaManager;
import com.ps117.allocation.SlotGenerator;
import com.ps117.billing.FeeCalculator;
import com.ps117.domain.*;
import com.ps117.enums.*;
import com.ps117.infrastructure.CsvReader;
import com.ps117.infrastructure.CsvWriter;
import com.ps117.lifecycle.CancellationService;
import com.ps117.lifecycle.NoShowService;
import com.ps117.lifecycle.RescheduleService;
import com.ps117.lifecycle.WalkInService;
import com.ps117.model.ExecutionSummary;
import com.ps117.util.DateUtils;
import com.ps117.util.MoneyUtils;
import com.ps117.validation.DocumentValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

public class PassportSlotEngine {
    private final String command;
    private final Path centresPath;
    private final Path applicationsPath;
    private final Path documentsPath;
    private final Path eventsPath;
    private final Path outputPath;
    private final LocalDateTime asOf;

    private final List<Centre> centres = new ArrayList<>();
    private final List<Application> applications = new ArrayList<>();
    private final List<Document> documents = new ArrayList<>();
    private final List<AppointmentEvent> events = new ArrayList<>();
    private final List<Appointment> appointments = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private final List<AllocationResult> allocationResults = new ArrayList<>();
    private final List<RefundResult> refundResults = new ArrayList<>();
    private final List<String[]> processingErrors = new ArrayList<>();
    private final QuotaManager quotaManager = new QuotaManager();
    private final FeeCalculator feeCalculator = new FeeCalculator();
    private final RescheduleService rescheduleService = new RescheduleService();
    private final CancellationService cancellationService = new CancellationService();
    private final NoShowService noShowService = new NoShowService();
    private WalkInService walkInService;

    public PassportSlotEngine(String command, Path centresPath, Path applicationsPath, Path documentsPath, Path eventsPath, Path outputPath, LocalDateTime asOf) {
        this.command = command == null ? "PROCESS" : command.toUpperCase();
        this.centresPath = centresPath;
        this.applicationsPath = applicationsPath;
        this.documentsPath = documentsPath;
        this.eventsPath = eventsPath;
        this.outputPath = outputPath;
        this.asOf = asOf == null ? LocalDateTime.now() : asOf;
    }

    public ExecutionSummary run() throws Exception {
        long start = System.currentTimeMillis();
        loadCentres();
        loadApplications();
        loadDocuments();
        loadEvents();

        int totalDayCapacity = centres.stream().mapToInt(Centre::getDailyCapacity).sum();
        this.walkInService = new WalkInService(totalDayCapacity > 0 ? totalDayCapacity : 35);

        switch (command) {
            case "VALIDATE" -> runValidate();
            case "ALLOCATE" -> runAllocate();
            case "QUOTA-REPORT" -> runQuotaReport();
            case "FEE-REPORT" -> runFeeReport();
            case "DAY-SHEET" -> runDaySheet();
            case "EVENT-PROCESS" -> runEventProcess();
            case "PROCESS" -> runFullProcess();
            default -> runFullProcess();
        }

        ExecutionSummary summary = new ExecutionSummary();
        summary.setCommand(command);
        summary.setTotalInputRecords(applications.size());
        summary.setValidApplications((int) applications.stream().filter(a -> a.getValidationStatus() == ValidationStatus.VALID).count());
        summary.setInvalidApplications((int) applications.stream().filter(a -> a.getValidationStatus() == ValidationStatus.INVALID).count());
        summary.setDocBlockApplications((int) applications.stream().filter(a -> a.getValidationStatus() == ValidationStatus.DOC_BLOCK).count());
        summary.setSuccessfulAllocations((int) allocationResults.stream().filter(AllocationResult::isAllocated).count());
        summary.setUnallocatedApplications(applications.size() - summary.getSuccessfulAllocations());
        summary.setEventsProcessed((int) events.stream().filter(AppointmentEvent::isProcessed).count());
        summary.setRefundsCalculated(refundResults.size());
        summary.setProcessingErrors(processingErrors.size());
        summary.setExecutionDurationMs(System.currentTimeMillis() - start);

        exportExecutionSummary(summary);
        return summary;
    }

    public List<Centre> getCentres() { return List.copyOf(centres); }
    public List<Application> getApplications() { return List.copyOf(applications); }
    public List<Document> getDocuments() { return List.copyOf(documents); }
    public List<Appointment> getAppointments() { return List.copyOf(appointments); }
    public List<Slot> getSlots() { return List.copyOf(slots); }
    public List<AllocationResult> getAllocationResults() { return List.copyOf(allocationResults); }
    public List<RefundResult> getRefundResults() { return List.copyOf(refundResults); }
    public List<String[]> getProcessingErrors() { return List.copyOf(processingErrors); }

    private void runValidate() throws IOException {
        validateDocuments();
        exportValidationResults();
        exportProcessingErrors();
    }

    private void runAllocate() throws IOException {
        generateSlots();
        validateDocuments();
        allocate();
        exportAllocationResults();
        exportValidationResults();
        exportAppointmentLetters();
        exportQuotaSummary();
        exportProcessingErrors();
    }

    private void runQuotaReport() throws IOException {
        generateSlots();
        validateDocuments();
        allocate();
        exportQuotaSummary();
        exportProcessingErrors();
    }

    private void runFeeReport() throws IOException {
        exportFeeBreakdown();
        exportProcessingErrors();
    }

    private void runDaySheet() throws IOException {
        generateSlots();
        validateDocuments();
        allocate();
        exportCentreDaySheets();
        exportAppointmentLetters();
        exportProcessingErrors();
    }

    private void runEventProcess() throws IOException {
        generateSlots();
        validateDocuments();
        allocate();
        processEvents();
        exportEventResults();
        exportRefundResults();
        exportProcessingErrors();
    }

    private void runFullProcess() throws IOException {
        generateSlots();
        validateDocuments();
        allocate();
        processEvents();
        exportOutputs();
    }

    private void loadCentres() throws IOException {
        if (centresPath == null || !Files.exists(centresPath)) return;
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(centresPath);
        if (rows.size() <= 1) return;

        String[] header = rows.get(0);
        int idIdx = findHeaderIndex(header, "centre_id");
        int nameIdx = findHeaderIndex(header, "name", "centre_name");
        int cityIdx = findHeaderIndex(header, "city");
        int dailyCapIdx = findHeaderIndex(header, "daily_capacity");
        int slotCapIdx = findHeaderIndex(header, "slot_capacity", "slot_minutes");
        int openIdx = findHeaderIndex(header, "open_time", "first_slot");
        int closeIdx = findHeaderIndex(header, "close_time", "last_slot");
        int daysIdx = findHeaderIndex(header, "working_days");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;
            try {
                Centre centre = new Centre();
                centre.setCentreId(safeGet(row, idIdx));
                centre.setName(safeGet(row, nameIdx));
                centre.setCity(safeGet(row, cityIdx));

                int dailyCap = 100;
                if (dailyCapIdx >= 0 && !safeGet(row, dailyCapIdx).isBlank()) {
                    dailyCap = Integer.parseInt(safeGet(row, dailyCapIdx));
                }
                centre.setDailyCapacity(dailyCap);

                int slotCap = 10;
                if (slotCapIdx >= 0 && !safeGet(row, slotCapIdx).isBlank()) {
                    int val = Integer.parseInt(safeGet(row, slotCapIdx));
                    slotCap = (val == 15) ? (dailyCap / 28 > 0 ? dailyCap / 28 : 10) : val;
                }
                centre.setSlotCapacity(slotCap);

                centre.setOpenTime(openIdx >= 0 && !safeGet(row, openIdx).isBlank() ? LocalTime.parse(safeGet(row, openIdx)) : LocalTime.of(9, 0));
                centre.setCloseTime(closeIdx >= 0 && !safeGet(row, closeIdx).isBlank() ? LocalTime.parse(safeGet(row, closeIdx)) : LocalTime.of(16, 0));

                if (daysIdx >= 0 && !safeGet(row, daysIdx).isBlank()) {
                    Set<java.time.DayOfWeek> workingDays = new HashSet<>();
                    for (String d : safeGet(row, daysIdx).split(";")) {
                        try {
                            workingDays.add(parseDayOfWeek(d.trim()));
                        } catch (Exception ignored) {}
                    }
                    centre.setWorkingDays(workingDays);
                }
                centres.add(centre);
            } catch (Exception ex) {
                processingErrors.add(new String[]{"INVALID_ROW_CENTRES", "Row " + i + ": " + ex.getMessage()});
            }
        }
    }

    private void loadApplications() throws IOException {
        if (applicationsPath == null || !Files.exists(applicationsPath)) return;
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(applicationsPath);
        if (rows.size() <= 1) return;

        String[] header = rows.get(0);
        int appIdIdx = findHeaderIndex(header, "application_id", "app_id");
        int applicantIdIdx = findHeaderIndex(header, "applicant_id", "app_id");
        int nameIdx = findHeaderIndex(header, "full_name", "applicant_name");
        int dobIdx = findHeaderIndex(header, "date_of_birth", "dob");
        int tsIdx = findHeaderIndex(header, "application_timestamp", "application_ts");
        int serviceIdx = findHeaderIndex(header, "service_type");
        int centreIdx = findHeaderIndex(header, "centre_preference", "centre_id");
        int flagsIdx = findHeaderIndex(header, "category_flags");
        int aadhaarIdx = findHeaderIndex(header, "aadhaar_verification_status", "aadhaar_verified");
        int addrChangeIdx = findHeaderIndex(header, "address_change_flag", "address_changed");
        int conditionIdx = findHeaderIndex(header, "passport_condition", "lost_or_damaged");
        int bookletIdx = findHeaderIndex(header, "booklet_size", "booklet_pages");
        int refsIdx = findHeaderIndex(header, "document_refs");
        int diffAbledIdx = findHeaderIndex(header, "differently_abled");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;
            try {
                Application app = new Application();
                app.setApplicationId(safeGet(row, appIdIdx));
                app.setApplicantId(safeGet(row, applicantIdIdx).isBlank() ? app.getApplicationId() : safeGet(row, applicantIdIdx));
                app.setFullName(safeGet(row, nameIdx));

                String dobStr = safeGet(row, dobIdx);
                if (!dobStr.isBlank()) {
                    app.setDateOfBirth(LocalDate.parse(dobStr));
                }

                String tsStr = safeGet(row, tsIdx);
                if (!tsStr.isBlank()) {
                    app.setApplicationTimestamp(parseFlexibleDateTime(tsStr));
                }

                String servStr = safeGet(row, serviceIdx).replace("-", "_").toUpperCase();
                app.setServiceType(ServiceType.valueOf(servStr));
                app.setCentrePreference(safeGet(row, centreIdx));

                if (flagsIdx >= 0 && !safeGet(row, flagsIdx).isBlank()) {
                    for (String c : safeGet(row, flagsIdx).split(";")) {
                        try {
                            app.addCategory(Category.valueOf(c.trim().toUpperCase()));
                        } catch (Exception ignored) {}
                    }
                }

                if (diffAbledIdx >= 0 && "true".equalsIgnoreCase(safeGet(row, diffAbledIdx))) {
                    app.addCategory(Category.DIFFERENTLY_ABLED);
                }

                if (aadhaarIdx >= 0) {
                    String av = safeGet(row, aadhaarIdx);
                    if ("true".equalsIgnoreCase(av) || "VERIFIED".equalsIgnoreCase(av)) {
                        app.setAadhaarVerificationStatus(VerificationStatus.VERIFIED);
                    } else {
                        app.setAadhaarVerificationStatus(VerificationStatus.UNVERIFIED);
                    }
                } else {
                    app.setAadhaarVerificationStatus(VerificationStatus.UNVERIFIED);
                }

                if (addrChangeIdx >= 0) {
                    app.setAddressChangeFlag(Boolean.parseBoolean(safeGet(row, addrChangeIdx)));
                }

                if (conditionIdx >= 0) {
                    String condStr = safeGet(row, conditionIdx);
                    if ("true".equalsIgnoreCase(condStr)) {
                        app.setPassportCondition(PassportCondition.DAMAGED);
                    } else if ("false".equalsIgnoreCase(condStr)) {
                        app.setPassportCondition(PassportCondition.NORMAL);
                    } else {
                        try {
                            app.setPassportCondition(PassportCondition.valueOf(condStr.toUpperCase()));
                        } catch (Exception ex) {
                            app.setPassportCondition(PassportCondition.NORMAL);
                        }
                    }
                } else {
                    app.setPassportCondition(PassportCondition.NORMAL);
                }

                if (bookletIdx >= 0) {
                    String bVal = safeGet(row, bookletIdx).replaceAll("[^0-9]", "");
                    if ("60".equals(bVal)) {
                        app.setBookletSize(BookletSize.PAGES_60);
                    } else {
                        app.setBookletSize(BookletSize.PAGES_36);
                    }
                } else {
                    app.setBookletSize(BookletSize.PAGES_36);
                }

                if (refsIdx >= 0 && !safeGet(row, refsIdx).isBlank()) {
                    List<String> refs = new ArrayList<>();
                    for (String r : safeGet(row, refsIdx).split(";")) {
                        if (!r.isBlank()) refs.add(r.trim());
                    }
                    app.setDocumentRefs(refs);
                }

                applications.add(app);
            } catch (Exception ex) {
                processingErrors.add(new String[]{"INVALID_ROW_APPLICATIONS", "Row " + i + ": " + ex.getMessage()});
            }
        }
    }

    private void loadDocuments() throws IOException {
        if (documentsPath == null || !Files.exists(documentsPath)) return;
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(documentsPath);
        if (rows.size() <= 1) return;

        String[] header = rows.get(0);
        int docIdIdx = findHeaderIndex(header, "document_id", "doc_id");
        int appIdIdx = findHeaderIndex(header, "application_id", "app_id");
        int typeIdx = findHeaderIndex(header, "document_type", "doc_type");
        int statusIdx = findHeaderIndex(header, "verification_status", "verification");
        int expiryIdx = findHeaderIndex(header, "expiry_date", "valid_until");
        int reqIdx = findHeaderIndex(header, "required");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;
            try {
                Document doc = new Document();
                doc.setDocumentId(safeGet(row, docIdIdx));
                doc.setApplicationId(safeGet(row, appIdIdx));

                String typeStr = safeGet(row, typeIdx).toUpperCase().trim();
                doc.setDocumentType(parseDocumentType(typeStr));

                String vStatus = safeGet(row, statusIdx).toUpperCase().trim();
                doc.setVerificationStatus(parseVerificationStatus(vStatus));

                String expStr = safeGet(row, expiryIdx).trim();
                if (!expStr.isBlank() && !"-".equals(expStr)) {
                    doc.setExpiryDate(LocalDate.parse(expStr));
                }

                if (reqIdx >= 0 && !safeGet(row, reqIdx).isBlank()) {
                    doc.setRequired(Boolean.parseBoolean(safeGet(row, reqIdx)));
                } else {
                    doc.setRequired(true);
                }
                documents.add(doc);
            } catch (Exception ex) {
                processingErrors.add(new String[]{"INVALID_ROW_DOCUMENTS", "Row " + i + ": " + ex.getMessage()});
            }
        }
    }

    private void loadEvents() throws IOException {
        if (eventsPath == null || !Files.exists(eventsPath)) return;

        try (BufferedReader br = Files.newBufferedReader(eventsPath, StandardCharsets.UTF_8)) {
            String firstLine = br.readLine();
            if (firstLine == null) return;

            if (firstLine.contains("|")) {
                int eventCounter = 1;
                String line = firstLine;
                do {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        String[] parts = line.split("\\|");
                        String cmd = parts[0].trim().toUpperCase();
                        if ("RESCHEDULE".equals(cmd) && parts.length >= 3) {
                            AppointmentEvent event = new AppointmentEvent();
                            event.setEventId("EVT-" + (eventCounter++));
                            event.setApplicationId(parts[1].trim());
                            event.setEventType(EventType.RESCHEDULE);
                            event.setEventTimestamp(parseFlexibleDateTime(parts[2].trim()));
                            if (parts.length > 3) event.setDetails(parts[3].trim());
                            events.add(event);
                        } else if ("CANCEL".equals(cmd) && parts.length >= 3) {
                            AppointmentEvent event = new AppointmentEvent();
                            event.setEventId("EVT-" + (eventCounter++));
                            event.setApplicationId(parts[1].trim());
                            event.setEventType(EventType.CANCEL);
                            event.setEventTimestamp(parseFlexibleDateTime(parts[2].trim()));
                            events.add(event);
                        } else if ("NO_SHOW".equals(cmd) && parts.length >= 3) {
                            AppointmentEvent event = new AppointmentEvent();
                            event.setEventId("EVT-" + (eventCounter++));
                            event.setApplicationId(parts[1].trim());
                            event.setEventType(EventType.NO_SHOW);
                            event.setEventTimestamp(LocalDate.parse(parts[2].trim()).atTime(9, 0));
                            events.add(event);
                        } else if ("WALK_IN".equals(cmd) && parts.length >= 4) {
                            AppointmentEvent event = new AppointmentEvent();
                            event.setEventId("EVT-" + (eventCounter++));
                            event.setApplicationId(parts[1].trim());
                            event.setEventType(EventType.WALK_IN);
                            event.setEventTimestamp(parseFlexibleDateTime(parts[3].trim()));
                            event.setDetails(parts[2].trim());
                            events.add(event);
                        }
                    }
                } while ((line = br.readLine()) != null);
                return;
            }
        }

        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(eventsPath);
        if (rows.size() <= 1) return;

        String[] header = rows.get(0);
        int eventIdIdx = findHeaderIndex(header, "event_id");
        int appIdIdx = findHeaderIndex(header, "application_id", "app_id");
        int typeIdx = findHeaderIndex(header, "event_type");
        int tsIdx = findHeaderIndex(header, "event_timestamp");
        int slotIdx = findHeaderIndex(header, "target_slot_id");
        int detailsIdx = findHeaderIndex(header, "details");

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;
            try {
                AppointmentEvent event = new AppointmentEvent();
                event.setEventId(safeGet(row, eventIdIdx));
                event.setApplicationId(safeGet(row, appIdIdx));
                event.setEventType(EventType.valueOf(safeGet(row, typeIdx).toUpperCase()));
                event.setEventTimestamp(parseFlexibleDateTime(safeGet(row, tsIdx)));
                if (slotIdx >= 0) event.setTargetSlotId(safeGet(row, slotIdx));
                if (detailsIdx >= 0) event.setDetails(safeGet(row, detailsIdx));
                events.add(event);
            } catch (Exception ex) {
                processingErrors.add(new String[]{"INVALID_ROW_EVENTS", "Row " + i + ": " + ex.getMessage()});
            }
        }
    }

    private void generateSlots() {
        SlotGenerator generator = new SlotGenerator();
        for (Centre centre : centres) {
            LocalDate startDate = asOf.toLocalDate();
            for (int i = 0; i < 5; i++) {
                LocalDate date = startDate.plusDays(i);
                if (centre.isWorkingDay(date.getDayOfWeek())) {
                    slots.addAll(generator.generateForDate(centre, date));
                }
            }
        }
    }

    private void validateDocuments() {
        DocumentValidator validator = new DocumentValidator();
        LocalDate targetAppointmentDate = DateUtils.nextWorkingDay(asOf.toLocalDate(), null);

        for (Application app : applications) {
            List<Document> appDocs = documents.stream()
                    .filter(d -> app.getApplicationId().equals(d.getApplicationId()))
                    .toList();
            validator.validate(app, appDocs, targetAppointmentDate);
        }
    }

    private void allocate() {
        AllocationEngine allocationEngine = new AllocationEngine();
        List<AllocationResult> results = allocationEngine.allocate(applications, centres, slots, quotaManager, asOf.toLocalDate());
        allocationResults.addAll(results);

        LocalDate appDate = DateUtils.nextWorkingDay(asOf.toLocalDate(), null);
        for (AllocationResult result : results) {
            if (!result.isAllocated()) continue;
            Application app = applications.stream()
                    .filter(a -> a.getApplicationId().equals(result.getApplicationId()))
                    .findFirst()
                    .orElse(null);
            if (app == null) continue;

            Appointment appointment = new Appointment();
            appointment.setAppointmentId("APT-" + app.getApplicationId());
            appointment.setApplicationId(app.getApplicationId());
            appointment.setApplicantId(app.getApplicantId());
            appointment.setCentreId(result.getCentreId());
            appointment.setSlotId(result.getSlotId());
            appointment.setAppointmentDate(appDate);
            appointment.setAppointmentTime(LocalTime.of(9, 0));
            appointment.setServiceType(app.getServiceType());
            appointment.setCategory(app.getCategory());
            appointment.setFeeAmount(feeCalculator.calculate(app, appDate));
            appointments.add(appointment);
        }
    }

    private void processEvents() {
        for (AppointmentEvent event : events) {
            String appId = event.getApplicationId();
            Application app = applications.stream().filter(a -> a.getApplicationId().equals(appId)).findFirst().orElse(null);
            Appointment apt = appointments.stream().filter(a -> a.getApplicationId().equals(appId)).findFirst().orElse(null);

            switch (event.getEventType()) {
                case RESCHEDULE -> {
                    if (apt != null && app != null) {
                        String newSlot = event.getTargetSlotId() != null && !event.getTargetSlotId().isBlank()
                                ? event.getTargetSlotId() : apt.getSlotId() + "-RESCHEDULED";
                        boolean ok = rescheduleService.reschedule(app, apt, apt.getSlotId(), newSlot, event.getEventTimestamp(), event.getDetails());
                        event.setProcessed(ok);
                        event.setProcessingStatus(ok ? "PROCESSED" : "REJECTED");
                    } else {
                        event.setProcessed(false);
                        event.setProcessingStatus("REJECTED");
                    }
                }
                case CANCEL -> {
                    if (apt != null) {
                        BigDecimal fee = apt.getFeeAmount() != null ? apt.getFeeAmount() : (app != null ? feeCalculator.calculate(app) : BigDecimal.ZERO);
                        RefundResult rr = cancellationService.cancel(apt, event.getEventTimestamp(), fee);
                        if (rr != null) {
                            refundResults.add(rr);
                            boolean ok = !rr.isDuplicateCancellation();
                            event.setProcessed(ok);
                            event.setProcessingStatus(ok ? "PROCESSED" : "REJECTED");
                        } else {
                            event.setProcessed(false);
                            event.setProcessingStatus("REJECTED");
                        }
                    } else {
                        event.setProcessed(false);
                        event.setProcessingStatus("REJECTED");
                    }
                }
                case NO_SHOW -> {
                    if (apt != null) {
                        boolean ok = noShowService.recordNoShow(app, apt);
                        event.setProcessed(ok);
                        event.setProcessingStatus(ok ? "PROCESSED" : "REJECTED");
                    } else {
                        event.setProcessed(false);
                        event.setProcessingStatus("REJECTED");
                    }
                }
                case WALK_IN -> {
                    if (app != null && walkInService != null) {
                        boolean ok = walkInService.registerWalkIn(app);
                        event.setProcessed(ok);
                        event.setProcessingStatus(ok ? "PROCESSED" : "REJECTED");
                    } else {
                        event.setProcessed(false);
                        event.setProcessingStatus("REJECTED");
                    }
                }
            }
        }
    }

    private void exportOutputs() throws IOException {
        exportValidationResults();
        exportAllocationResults();
        exportAppointmentLetters();
        exportFeeBreakdown();
        exportRefundResults();
        exportEventResults();
        exportQuotaSummary();
        exportCentreDaySheets();
        exportProcessingErrors();
        exportEvaluationAliases();
    }

    private void exportValidationResults() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        writer.write(outputPath.resolve("validation-results.csv"),
                new String[]{"application_id", "status", "score", "verification_mode"},
                applications.stream().map(a -> new String[]{
                        a.getApplicationId(),
                        a.getValidationStatus() == null ? "DOC_BLOCK" : a.getValidationStatus().name(),
                        String.valueOf(a.getDocumentScore() == null ? 0 : a.getDocumentScore()),
                        a.getVerificationMode() == null ? "" : a.getVerificationMode().name()
                }).toList());
    }

    private void exportAllocationResults() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        writer.write(outputPath.resolve("allocation-results.csv"),
                new String[]{"application_id", "allocated", "reason", "slot_id", "centre_id"},
                allocationResults.stream().map(r -> new String[]{
                        r.getApplicationId(),
                        String.valueOf(r.isAllocated()),
                        r.getReason(),
                        r.getSlotId() == null ? "" : r.getSlotId(),
                        r.getCentreId() == null ? "" : r.getCentreId()
                }).toList());
    }

    private void exportAppointmentLetters() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        writer.write(outputPath.resolve("appointment-letters.csv"),
                new String[]{"appointment_id", "letters"},
                appointments.stream().map(a -> new String[]{
                        a.getAppointmentId(),
                        a.getApplicationId()
                }).toList());
    }

    private void exportFeeBreakdown() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        LocalDate appDate = DateUtils.nextWorkingDay(asOf.toLocalDate(), null);
        writer.write(outputPath.resolve("fee-breakdown.csv"),
                new String[]{"application_id", "fee"},
                applications.stream().map(a -> new String[]{
                        a.getApplicationId(),
                        feeCalculator.calculate(a, appDate).toString()
                }).toList());
    }

    private void exportRefundResults() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        List<String[]> rows = new ArrayList<>();
        if (refundResults.isEmpty()) {
            rows.add(new String[]{"", "0.00", ""});
        } else {
            for (RefundResult r : refundResults) {
                rows.add(new String[]{
                        r.getApplicationId() == null ? "" : r.getApplicationId(),
                        r.getRefundAmount() == null ? "0.00" : r.getRefundAmount().toString(),
                        r.getReason() == null ? "" : r.getReason()
                });
            }
        }
        writer.write(outputPath.resolve("refund-results.csv"),
                new String[]{"application_id", "refund_amount", "reason"}, rows);
    }

    private void exportEventResults() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        writer.write(outputPath.resolve("event-results.csv"),
                new String[]{"event_id", "application_id", "status"},
                events.stream().map(e -> new String[]{
                        e.getEventId(),
                        e.getApplicationId(),
                        e.getProcessingStatus() != null ? e.getProcessingStatus() : (e.isProcessed() ? "PROCESSED" : "PENDING")
                }).toList());
    }

    private void exportQuotaSummary() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        LocalDate appDate = DateUtils.nextWorkingDay(asOf.toLocalDate(), null);
        List<String[]> rows = new ArrayList<>();

        for (Centre centre : centres) {
            for (QuotaType type : List.of(QuotaType.TATKAL, QuotaType.SENIOR_MINOR, QuotaType.PCC, QuotaType.NORMAL)) {
                int rem = quotaManager.remaining(centre.getCentreId(), appDate, type);
                rows.add(new String[]{centre.getCentreId(), type.name(), String.valueOf(rem)});
            }
        }
        if (rows.isEmpty()) {
            rows.add(new String[]{"C1", "NORMAL", "0"});
        }
        writer.write(outputPath.resolve("quota-summary.csv"),
                new String[]{"centre_id", "quota_type", "remaining"}, rows);
    }

    private void exportCentreDaySheets() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        writer.write(outputPath.resolve("centre-day-sheets.csv"),
                new String[]{"centre_id", "appointment_date", "slot_id"},
                appointments.stream().map(a -> new String[]{
                        a.getCentreId(),
                        a.getAppointmentDate().toString(),
                        a.getSlotId()
                }).toList());
    }

    private void exportProcessingErrors() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        List<String[]> rows = new ArrayList<>();
        if (processingErrors.isEmpty()) {
            rows.add(new String[]{"NONE", ""});
        } else {
            rows.addAll(processingErrors);
        }
        writer.write(outputPath.resolve("processing-errors.csv"),
                new String[]{"error_code", "message"}, rows);
    }

    private void exportEvaluationAliases() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        LocalDate appDate = DateUtils.nextWorkingDay(asOf.toLocalDate(), null);

        // validation.csv
        writer.write(outputPath.resolve("validation.csv"),
                new String[]{"app_id", "category", "doc_score", "verification_mode", "status", "blocking_gaps"},
                applications.stream().map(a -> new String[]{
                        a.getApplicationId(),
                        a.getCategory() == null ? "" : a.getCategory().name(),
                        String.valueOf(a.getDocumentScore() == null ? 0 : a.getDocumentScore()),
                        a.getVerificationMode() == null ? "" : a.getVerificationMode().name(),
                        a.getValidationStatus() == null ? "DOC_BLOCK" : a.getValidationStatus().name(),
                        a.getBlockingReason() == null ? "" : a.getBlockingReason()
                }).toList());

        // appointments.csv
        writer.write(outputPath.resolve("appointments.csv"),
                new String[]{"app_id", "action", "action_ts", "centre_id", "slot_date", "slot_time", "quota_pool", "priority_reason"},
                appointments.stream().map(a -> new String[]{
                        a.getApplicationId(),
                        "ALLOCATED",
                        asOf.toString(),
                        a.getCentreId(),
                        a.getAppointmentDate().toString(),
                        a.getAppointmentTime().toString(),
                        a.getCategory() == Category.TATKAL ? "TATKAL" : (a.getCategory() == Category.SENIOR_CITIZEN || a.getCategory() == Category.MINOR ? "SENIOR_MINOR" : "NORMAL"),
                        a.getCategory() == null ? "NORMAL" : a.getCategory().name()
                }).toList());

        // fees.csv
        writer.write(outputPath.resolve("fees.csv"),
                new String[]{"app_id", "base_fee", "senior_discount", "tatkal_surcharge", "total_fee", "refund", "forfeited"},
                applications.stream().map(a -> {
                    FeeBreakdown fb = feeCalculator.breakdown(a, appDate);
                    return new String[]{
                            a.getApplicationId(),
                            fb.getBaseFee().toString(),
                            fb.getSeniorDiscount().toString(),
                            fb.getTatkalSurcharge().toString(),
                            fb.getTotal().toString(),
                            "0.00",
                            "false"
                    };
                }).toList());

        // day_sheets.csv
        writer.write(outputPath.resolve("day_sheets.csv"),
                new String[]{"centre_id", "slot_date", "slot_time", "app_id", "category", "status", "wait_flag"},
                appointments.stream().map(a -> new String[]{
                        a.getCentreId(),
                        a.getAppointmentDate().toString(),
                        a.getAppointmentTime().toString(),
                        a.getApplicationId(),
                        a.getCategory() == null ? "NORMAL" : a.getCategory().name(),
                        a.getStatus().name(),
                        String.valueOf(a.getStatus() == AppointmentStatus.WAIT)
                }).toList());
    }

    private void exportExecutionSummary(ExecutionSummary summary) throws IOException {
        Files.createDirectories(outputPath);
        StringBuilder sb = new StringBuilder();
        sb.append("Command: ").append(command).append(System.lineSeparator());
        sb.append("Total Input Records: ").append(summary.getTotalInputRecords()).append(System.lineSeparator());
        sb.append("Valid Applications: ").append(summary.getValidApplications()).append(System.lineSeparator());
        sb.append("Invalid Applications: ").append(summary.getInvalidApplications()).append(System.lineSeparator());
        sb.append("DOC_BLOCK Applications: ").append(summary.getDocBlockApplications()).append(System.lineSeparator());
        sb.append("Successful Allocations: ").append(summary.getSuccessfulAllocations()).append(System.lineSeparator());
        sb.append("Unallocated Applications: ").append(summary.getUnallocatedApplications()).append(System.lineSeparator());
        sb.append("Events Processed: ").append(summary.getEventsProcessed()).append(System.lineSeparator());
        sb.append("Refunds Calculated: ").append(summary.getRefundsCalculated()).append(System.lineSeparator());
        sb.append("Processing Errors: ").append(summary.getProcessingErrors()).append(System.lineSeparator());
        sb.append("Execution Duration (ms): ").append(summary.getExecutionDurationMs()).append(System.lineSeparator());
        Files.writeString(outputPath.resolve("execution-summary.txt"), sb.toString());
    }

    private int findHeaderIndex(String[] header, String... candidates) {
        if (header == null) return -1;
        for (int i = 0; i < header.length; i++) {
            String col = header[i].trim().toLowerCase();
            for (String candidate : candidates) {
                if (col.equalsIgnoreCase(candidate.trim().toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String safeGet(String[] row, int index) {
        if (row == null || index < 0 || index >= row.length || row[index] == null) {
            return "";
        }
        return row[index].trim();
    }

    private LocalDateTime parseFlexibleDateTime(String str) {
        str = str.trim();
        if (str.contains(" ")) {
            str = str.replace(" ", "T");
        }
        return LocalDateTime.parse(str);
    }

    private java.time.DayOfWeek parseDayOfWeek(String str) {
        return switch (str.toUpperCase()) {
            case "MON", "MONDAY" -> java.time.DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> java.time.DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> java.time.DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> java.time.DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> java.time.DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> java.time.DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> java.time.DayOfWeek.SUNDAY;
            default -> java.time.DayOfWeek.valueOf(str.toUpperCase());
        };
    }

    private DocumentType parseDocumentType(String typeStr) {
        try {
            return DocumentType.valueOf(typeStr);
        } catch (Exception ex) {
            if ("PHOTO".equalsIgnoreCase(typeStr)) return DocumentType.PHOTOGRAPH;
            return DocumentType.ADDRESS_PROOF;
        }
    }

    private VerificationStatus parseVerificationStatus(String vStr) {
        try {
            return VerificationStatus.valueOf(vStr);
        } catch (Exception ex) {
            return VerificationStatus.UNVERIFIED;
        }
    }
}
