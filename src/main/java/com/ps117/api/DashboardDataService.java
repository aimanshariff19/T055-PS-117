package com.ps117.api;

import com.ps117.allocation.PriorityComparator;
import com.ps117.app.PassportSlotEngine;
import com.ps117.billing.FeeCalculator;
import com.ps117.domain.AllocationResult;
import com.ps117.domain.Application;
import com.ps117.domain.Appointment;
import com.ps117.domain.Centre;
import com.ps117.domain.Document;
import com.ps117.domain.Slot;
import com.ps117.enums.Category;
import com.ps117.enums.BookletSize;
import com.ps117.enums.DocumentType;
import com.ps117.enums.PassportCondition;
import com.ps117.enums.ServiceType;
import com.ps117.enums.VerificationStatus;
import com.ps117.enums.EventType;
import com.ps117.infrastructure.ApplicationConfig;
import com.ps117.model.ExecutionSummary;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DashboardDataService {
    private static final String[] APPLICATION_HEADERS = {
            "application_id", "applicant_id", "full_name", "date_of_birth", "application_timestamp",
            "service_type", "centre_preference", "category_flags", "aadhaar_verification_status",
            "address_change_flag", "passport_condition", "booklet_size", "document_refs"
    };
    private static final String[] DOCUMENT_HEADERS = {
            "document_id", "application_id", "document_type", "verification_status", "expiry_date", "required"
    };
    private static final String[] CENTRE_HEADERS = {
            "centre_id", "name", "city", "daily_capacity", "slot_capacity", "open_time", "close_time", "working_days"
    };
    private static final String[] EVENT_HEADERS = {
            "event_id", "application_id", "event_type", "event_timestamp", "target_slot_id", "details"
    };
    private static final List<Report> REPORTS = List.of(
            new Report("allocation-results", "Allocation results", "allocation-results.csv", "Allocation decisions written by the Java engine."),
            new Report("appointment-letters", "Appointment letters", "appointment-letters.csv", "Appointment letter records written by the Java engine."),
            new Report("centre-day-sheets", "Centre day sheets", "centre-day-sheets.csv", "Centre and slot rows written by the Java engine."),
            new Report("quota-summary", "Quota summaries", "quota-summary.csv", "The current engine writes a placeholder quota row; this is not live quota availability."),
            new Report("fee-breakdown", "Fee breakdown", "fee-breakdown.csv", "The current engine writes zero-valued placeholders; this is not a payment ledger."),
            new Report("validation-results", "Validation results", "validation-results.csv", "Document validation statuses written by the Java engine."),
            new Report("processing-errors", "Processing errors", "processing-errors.csv", "Processing error report written by the Java engine."),
            new Report("refund-results", "Refund results", "refund-results.csv", "The current engine writes a placeholder refund row; this is not a refund ledger."),
            new Report("event-results", "Event results", "event-results.csv", "Event processing statuses written by the Java engine.")
    );

    private final ApplicationConfig config;
    private volatile RunSnapshot latestRun;

    DashboardDataService(ApplicationConfig config) {
        this.config = config;
    }

    Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "application", config.applicationName(),
                "version", config.applicationVersion(),
                "runtime", Runtime.version().toString()
        );
    }

    Map<String, Object> settings(int port) {
        Map<String, Object> inputFiles = new LinkedHashMap<>();
        inputFiles.put("centres", config.defaultCentresPath().toString());
        inputFiles.put("applications", config.defaultApplicationsPath().toString());
        inputFiles.put("documents", config.defaultDocumentsPath().toString());
        inputFiles.put("events", config.defaultEventsPath().toString());
        return Map.of(
                "port", port,
                "commands", Arrays.stream(config.commands().split(",")).map(String::trim).toList(),
                "inputFiles", inputFiles,
                "outputDirectory", config.defaultOutputPath().toString()
        );
    }

    Map<String, Object> summary() throws IOException {
        List<Map<String, String>> applications = readRows(config.defaultApplicationsPath(), APPLICATION_HEADERS);
        List<Map<String, String>> documents = readRows(config.defaultDocumentsPath(), DOCUMENT_HEADERS);
        RunSnapshot run = latestRun;

        long verifiedDocuments = documents.stream()
                .filter(row -> "VERIFIED".equalsIgnoreCase(row.get("verification_status")))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalApplications", applications.size());
        response.put("totalDocuments", documents.size());
        response.put("verifiedDocuments", verifiedDocuments);
        response.put("documentVerificationCompletionPercent", documents.isEmpty()
                ? null : Math.round(verifiedDocuments * 100.0 / documents.size()));
        response.put("processingRunAvailable", run != null);
        response.put("validApplications", run == null ? null : run.summary().getValidApplications());
        response.put("invalidApplications", run == null ? null : run.summary().getInvalidApplications());
        response.put("docBlockedApplications", run == null ? null : run.summary().getDocBlockApplications());
        response.put("allocatedApplications", run == null ? null : run.summary().getSuccessfulAllocations());
        response.put("unallocatedApplications", run == null ? null : run.summary().getUnallocatedApplications());
        response.put("availableSlots", run == null ? null : remainingSlots(run));
        response.put("feesCollected", null);
        response.put("feesCollectedAvailable", false);
        response.put("statusNote", run == null
                ? "Allocation and validation totals are available after running Java processing."
                : "Allocation and validation totals reflect the most recent processing run in this server session.");
        return response;
    }

    Map<String, Object> applications() throws IOException {
        List<Map<String, String>> rows = readRows(config.defaultApplicationsPath(), APPLICATION_HEADERS);
        List<Map<String, String>> documents = readRows(config.defaultDocumentsPath(), DOCUMENT_HEADERS);
        rows.forEach(this::validateApplication);
        documents.forEach(this::validateDocument);
        RunSnapshot run = latestRun;
        Map<String, Application> processed = applicationsById(run);
        Map<String, List<Map<String, String>>> docsByApplication = groupBy(documents, "application_id");

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String id = value(row, "application_id");
            Application engineApplication = processed.get(id);
            List<Map<String, String>> appDocuments = docsByApplication.getOrDefault(id, List.of());
            String documentStatus = appDocuments.isEmpty() ? "NO_DOCUMENTS"
                    : appDocuments.stream().allMatch(doc -> "VERIFIED".equalsIgnoreCase(doc.get("verification_status")))
                    ? "VERIFIED" : "INCOMPLETE";

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("applicationId", id);
            record.put("applicantId", value(row, "applicant_id"));
            record.put("applicantName", value(row, "full_name"));
            record.put("dateOfBirth", parseDate(value(row, "date_of_birth"), "applications.csv", id).toString());
            record.put("applicationDate", parseDateTime(value(row, "application_timestamp"), "applications.csv", id).toString());
            record.put("passportType", value(row, "service_type"));
            record.put("serviceType", value(row, "service_type"));
            record.put("preferredCentreId", value(row, "centre_preference"));
            record.put("priorityFlags", splitFlags(row.get("category_flags")));
            record.put("priorityCategory", engineApplication == null || engineApplication.getCategory() == null
                    ? null : engineApplication.getCategory().name());
            record.put("applicationStatus", engineApplication == null ? "NOT_PROCESSED"
                    : engineApplication.getApplicationStatus().name());
            record.put("validationStatus", engineApplication == null ? "NOT_PROCESSED"
                    : engineApplication.getValidationStatus().name());
            record.put("documentStatus", documentStatus);
            record.put("validationErrors", engineApplication == null || engineApplication.getBlockingReason() == null
                    ? List.of() : List.of(engineApplication.getBlockingReason()));
            record.put("allocatedCentreId", engineApplication == null ? null : engineApplication.getAllocatedCentreId());
            records.add(record);
        }
        return Map.of("processed", run != null, "applications", records);
    }

    Map<String, Object> appointments() {
        RunSnapshot run = latestRun;
        if (run == null) {
            return Map.of("processed", false, "appointments", List.of(),
                    "message", "Run the Java engine to populate allocation results.");
        }
        Map<String, Application> apps = applicationsById(run);
        Map<String, String> centreNames = centreNames(run.engine().getCentres());
        List<Map<String, Object>> appointments = run.engine().getAppointments().stream()
                .map(appointment -> appointment(appointment, apps, centreNames))
                .toList();
        return Map.of("processed", true, "appointments", appointments);
    }

    Map<String, Object> allocationResults() {
        RunSnapshot run = latestRun;
        if (run == null) {
            return Map.of("processed", false, "results", List.of(),
                    "message", "Run the Java engine to populate allocation results.");
        }
        return Map.of("processed", true, "results", run.engine().getAllocationResults().stream()
                .map(this::allocationResult).toList());
    }

    Map<String, Object> documents() throws IOException {
        List<Map<String, String>> rows = readRows(config.defaultDocumentsPath(), DOCUMENT_HEADERS);
        rows.forEach(this::validateDocument);
        Map<String, String> applicants = applicantNames();
        Map<String, Application> apps = applicationsById(latestRun);
        List<Map<String, Object>> records = rows.stream().map(row -> {
            String applicationId = value(row, "application_id");
            Application app = apps.get(applicationId);
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("documentId", value(row, "document_id"));
            document.put("applicationId", applicationId);
            document.put("applicantName", applicants.get(applicationId));
            document.put("documentType", value(row, "document_type"));
            document.put("verificationStatus", value(row, "verification_status"));
            document.put("expiryDate", parseDate(value(row, "expiry_date"), "documents.csv", value(row, "document_id")).toString());
            document.put("required", parseBoolean(value(row, "required"), "documents.csv", value(row, "document_id")));
            document.put("verificationDate", null);
            document.put("validationStatus", app == null ? "NOT_PROCESSED" : app.getValidationStatus().name());
            document.put("validationErrors", app == null || app.getBlockingReason() == null
                    ? List.of() : List.of(app.getBlockingReason()));
            return document;
        }).toList();
        return Map.of("processed", latestRun != null, "documents", records);
    }

    Map<String, Object> centres() throws IOException {
        List<Map<String, String>> rows = readRows(config.defaultCentresPath(), CENTRE_HEADERS);
        rows.forEach(this::validateCentre);
        RunSnapshot run = latestRun;
        Map<String, Integer> allocatedByCentre = new HashMap<>();
        Map<String, Integer> availableByCentre = new HashMap<>();
        if (run != null) {
            run.engine().getAppointments().forEach(appointment ->
                    allocatedByCentre.merge(appointment.getCentreId(), 1, Integer::sum));
            LocalDate appointmentDate = run.asOf().toLocalDate().plusDays(1);
            for (Slot slot : run.engine().getSlots()) {
                if (appointmentDate.equals(slot.getDate())) {
                    availableByCentre.merge(slot.getCentreId(), slot.getAvailableCapacity(), Integer::sum);
                }
            }
        }

        List<Map<String, Object>> centres = rows.stream().map(row -> {
            String id = value(row, "centre_id");
            int capacity = parseInteger(value(row, "daily_capacity"), "centres.csv", id);
            Integer allocated = run == null ? null : allocatedByCentre.getOrDefault(id, 0);
            Map<String, Object> centre = new LinkedHashMap<>();
            centre.put("centreId", id);
            centre.put("name", value(row, "name"));
            centre.put("city", value(row, "city"));
            centre.put("dailyCapacity", capacity);
            centre.put("slotCapacity", parseInteger(value(row, "slot_capacity"), "centres.csv", id));
            centre.put("openTime", value(row, "open_time"));
            centre.put("closeTime", value(row, "close_time"));
            centre.put("workingDays", splitFlags(row.get("working_days")));
            centre.put("allocatedSlots", allocated);
            centre.put("availableSlots", run == null ? null : availableByCentre.getOrDefault(id, 0));
            centre.put("utilizationPercent", allocated == null || capacity == 0
                    ? null : Math.round(allocated * 100.0 / capacity));
            centre.put("capacityNote", run == null
                    ? "Allocation counts are available after processing."
                    : "Available slot count is summed from Java-generated slots for the next appointment date.");
            return centre;
        }).toList();
        return Map.of("processed", run != null, "centres", centres);
    }

    Map<String, Object> priorities() throws IOException {
        List<Map<String, Object>> categories = Arrays.stream(Category.values())
                .sorted(Comparator.comparingInt(new PriorityComparator()::priorityRank))
                .map(category -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", category.name());
                    row.put("rank", new PriorityComparator().priorityRank(category));
                    return row;
                }).toList();
        return Map.of("categories", categories,
                "source", "com.ps117.enums.Category and com.ps117.allocation.PriorityComparator");
    }

    Map<String, Object> billing() {
        RunSnapshot run = latestRun;
        if (run == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("processed", false);
            response.put("feeEstimatesAvailable", false);
            response.put("totalEstimatedFees", null);
            response.put("totalCollected", null);
            response.put("paymentStatusAvailable", false);
            response.put("refundStatusAvailable", false);
            response.put("transactions", List.of());
            response.put("message", "Run the Java engine to calculate fee estimates. Payment collection and refund ledgers are not implemented.");
            return response;
        }
        FeeCalculator calculator = new FeeCalculator();
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> estimates = new ArrayList<>();
        for (Application app : run.engine().getApplications()) {
            BigDecimal estimate = calculator.calculate(app);
            total = total.add(estimate);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applicationId", app.getApplicationId());
            row.put("applicantName", app.getFullName());
            row.put("feeCategory", app.getServiceType() == null ? null : app.getServiceType().name());
            row.put("estimatedAmount", estimate);
            row.put("paymentStatus", "NOT_TRACKED");
            row.put("refundStatus", "NOT_TRACKED");
            estimates.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processed", true);
        result.put("feeEstimatesAvailable", true);
        result.put("totalEstimatedFees", total);
        result.put("totalCollected", null);
        result.put("paymentStatusAvailable", false);
        result.put("refundStatusAvailable", false);
        result.put("transactions", estimates);
        result.put("message", "Amounts are calculated fee estimates from FeeCalculator, not collected payments. Payment and refund states are not tracked.");
        return result;
    }

    Map<String, Object> reports() throws IOException {
        Path reportDirectory = latestRun == null ? config.defaultOutputPath() : latestRun.outputDirectory();
        List<Map<String, Object>> available = new ArrayList<>();
        for (Report report : REPORTS) {
            Path file = reportDirectory.resolve(report.fileName()).normalize();
            if (reportIsSafe(reportDirectory, file)) {
                available.add(Map.of(
                        "id", report.id(),
                        "name", report.name(),
                        "fileName", report.fileName(),
                        "downloadUrl", "/api/reports/" + report.id(),
                        "sizeBytes", Files.size(file),
                        "note", report.note()
                ));
            }
        }
        return Map.of(
                "processed", latestRun != null,
                "source", latestRun == null ? "configured output directory" : "latest API-triggered engine run",
                "reports", available
        );
    }

    Path reportFile(String id) throws IOException {
        Report report = REPORTS.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(404, "REPORT_NOT_FOUND", "Unknown report: " + id));
        Path directory = latestRun == null ? config.defaultOutputPath() : latestRun.outputDirectory();
        Path file = directory.resolve(report.fileName()).normalize();
        if (!reportIsSafe(directory, file)) {
            throw new ApiException(404, "REPORT_NOT_AVAILABLE", "This report is not available yet.");
        }
        return file.toRealPath();
    }

    Map<String, Object> process(LocalDateTime asOf) throws Exception {
        synchronized (this) {
            readRows(config.defaultApplicationsPath(), APPLICATION_HEADERS).forEach(this::validateApplication);
            readRows(config.defaultDocumentsPath(), DOCUMENT_HEADERS).forEach(this::validateDocument);
            readRows(config.defaultCentresPath(), CENTRE_HEADERS).forEach(this::validateCentre);
            readRows(config.defaultEventsPath(), EVENT_HEADERS).forEach(this::validateEvent);

            Path outputRoot = config.defaultOutputPath().toAbsolutePath().normalize();
            Files.createDirectories(outputRoot);
            outputRoot = outputRoot.toRealPath();
            Path runRoot = outputRoot.resolve("dashboard-runs").normalize();
            if (!runRoot.startsWith(outputRoot)) {
                throw new ApiException(500, "INVALID_OUTPUT_PATH", "Configured output directory is invalid.");
            }
            Files.createDirectories(runRoot);
            runRoot = runRoot.toRealPath();
            if (!runRoot.startsWith(outputRoot)) {
                throw new ApiException(500, "INVALID_OUTPUT_PATH", "Configured output directory is invalid.");
            }
            Path runDirectory = Files.createTempDirectory(runRoot, "run-" + UUID.randomUUID() + "-");
            PassportSlotEngine engine = new PassportSlotEngine(
                    "PROCESS",
                    config.defaultCentresPath(),
                    config.defaultApplicationsPath(),
                    config.defaultDocumentsPath(),
                    config.defaultEventsPath(),
                    runDirectory,
                    asOf
            );
            ExecutionSummary executionSummary = engine.run();
            RunSnapshot snapshot = new RunSnapshot(engine, executionSummary, asOf, runDirectory, LocalDateTime.now());
            latestRun = snapshot;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "completed");
            result.put("processedAt", snapshot.completedAt().toString());
            result.put("summary", executionSummary);
            result.put("reports", reports());
            return result;
        }
    }

    private long remainingSlots(RunSnapshot run) {
        LocalDate nextAppointmentDate = run.asOf().toLocalDate().plusDays(1);
        return run.engine().getSlots().stream()
                .filter(slot -> nextAppointmentDate.equals(slot.getDate()))
                .mapToLong(Slot::getAvailableCapacity)
                .sum();
    }

    private Map<String, Object> appointment(Appointment appointment, Map<String, Application> apps,
                                             Map<String, String> centreNames) {
        Application app = apps.get(appointment.getApplicationId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appointmentId", appointment.getAppointmentId());
        result.put("applicationId", appointment.getApplicationId());
        result.put("applicantName", app == null ? null : app.getFullName());
        result.put("centreId", appointment.getCentreId());
        result.put("centreName", centreNames.get(appointment.getCentreId()));
        result.put("appointmentDate", appointment.getAppointmentDate() == null ? null : appointment.getAppointmentDate().toString());
        result.put("appointmentTime", appointment.getAppointmentTime() == null ? null : appointment.getAppointmentTime().toString());
        result.put("slotId", appointment.getSlotId());
        result.put("priorityCategory", appointment.getCategory());
        result.put("allocationStatus", "ALLOCATED");
        return result;
    }

    private Map<String, Object> allocationResult(AllocationResult allocation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", allocation.getApplicationId());
        result.put("allocated", allocation.isAllocated());
        result.put("reason", allocation.getReason());
        result.put("slotId", allocation.getSlotId());
        result.put("centreId", allocation.getCentreId());
        result.put("priorityCategory", allocation.getCategory());
        return result;
    }

    private Map<String, Application> applicationsById(RunSnapshot run) {
        if (run == null) return Map.of();
        Map<String, Application> result = new HashMap<>();
        run.engine().getApplications().forEach(application -> result.put(application.getApplicationId(), application));
        return result;
    }

    private Map<String, String> applicantNames() throws IOException {
        Map<String, String> result = new HashMap<>();
        for (Map<String, String> row : readRows(config.defaultApplicationsPath(), APPLICATION_HEADERS)) {
            result.put(row.get("application_id"), row.get("full_name"));
        }
        return result;
    }

    private Map<String, String> centreNames(List<Centre> centres) {
        Map<String, String> result = new HashMap<>();
        centres.forEach(centre -> result.put(centre.getCentreId(), centre.getName()));
        return result;
    }

    private Map<String, List<Map<String, String>>> groupBy(List<Map<String, String>> rows, String key) {
        Map<String, List<Map<String, String>>> result = new HashMap<>();
        rows.forEach(row -> result.computeIfAbsent(value(row, key), ignored -> new ArrayList<>()).add(row));
        return result;
    }

    private List<Map<String, String>> readRows(Path path, String... requiredHeaders) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ApiException(503, "INPUT_UNAVAILABLE", "Required input file is missing or unreadable: " + path.getFileName());
        }
        try (CSVParser parser = CSVParser.parse(path, StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build())) {
            Set<String> headers = parser.getHeaderMap().keySet();
            for (String required : requiredHeaders) {
                if (!headers.contains(required)) {
                    throw new ApiException(422, "INVALID_CSV_SCHEMA",
                            path.getFileName() + " is missing required column: " + required);
                }
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    throw new ApiException(422, "MALFORMED_CSV",
                            path.getFileName() + " row " + record.getRecordNumber() + " has an inconsistent number of columns.");
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.get(header).trim());
                }
                rows.add(row);
            }
            return rows;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiException(422, "MALFORMED_CSV",
                    "Could not read " + path.getFileName() + ": " + exception.getMessage());
        }
    }

    private String value(Map<String, String> row, String name) {
        return row.getOrDefault(name, "");
    }

    private List<String> splitFlags(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(";")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    private LocalDate parseDate(String value, String file, String recordId) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(422, "INVALID_CSV_VALUE",
                    file + " has an invalid date for record " + recordId + ".");
        }
    }

    private LocalDateTime parseDateTime(String value, String file, String recordId) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(422, "INVALID_CSV_VALUE",
                    file + " has an invalid date-time for record " + recordId + ".");
        }
    }

    private int parseInteger(String value, String file, String recordId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(422, "INVALID_CSV_VALUE",
                    file + " has an invalid integer for record " + recordId + ".");
        }
    }

    private boolean parseBoolean(String value, String file, String recordId) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new ApiException(422, "INVALID_CSV_VALUE",
                file + " has an invalid boolean for record " + recordId + ".");
    }

    private void validateApplication(Map<String, String> row) {
        String id = value(row, "application_id");
        requireValue(row, "application_id", "applications.csv", id);
        requireValue(row, "applicant_id", "applications.csv", id);
        requireValue(row, "full_name", "applications.csv", id);
        parseDate(value(row, "date_of_birth"), "applications.csv", id);
        parseDateTime(value(row, "application_timestamp"), "applications.csv", id);
        parseEnum(value(row, "service_type"), ServiceType.class, "applications.csv", id);
        parseEnum(value(row, "aadhaar_verification_status"), VerificationStatus.class, "applications.csv", id);
        parseEnum(value(row, "passport_condition"), PassportCondition.class, "applications.csv", id);
        parseEnum("PAGES_" + value(row, "booklet_size").replace("pages", "").replace("-", "_"),
                BookletSize.class, "applications.csv", id);
        parseBoolean(value(row, "address_change_flag"), "applications.csv", id);
        for (String category : splitFlags(row.get("category_flags"))) {
            parseEnum(category, Category.class, "applications.csv", id);
        }
    }

    private void validateDocument(Map<String, String> row) {
        String id = value(row, "document_id");
        requireValue(row, "document_id", "documents.csv", id);
        requireValue(row, "application_id", "documents.csv", id);
        parseEnum(value(row, "document_type"), DocumentType.class, "documents.csv", id);
        parseEnum(value(row, "verification_status"), VerificationStatus.class, "documents.csv", id);
        parseDate(value(row, "expiry_date"), "documents.csv", id);
        parseBoolean(value(row, "required"), "documents.csv", id);
    }

    private void validateCentre(Map<String, String> row) {
        String id = value(row, "centre_id");
        requireValue(row, "centre_id", "centres.csv", id);
        requireValue(row, "name", "centres.csv", id);
        parseInteger(value(row, "daily_capacity"), "centres.csv", id);
        parseInteger(value(row, "slot_capacity"), "centres.csv", id);
        parseTime(value(row, "open_time"), "centres.csv", id);
        parseTime(value(row, "close_time"), "centres.csv", id);
    }

    private void validateEvent(Map<String, String> row) {
        String id = value(row, "event_id");
        requireValue(row, "event_id", "events.csv", id);
        requireValue(row, "application_id", "events.csv", id);
        parseEnum(value(row, "event_type"), EventType.class, "events.csv", id);
        parseDateTime(value(row, "event_timestamp"), "events.csv", id);
    }

    private LocalTime parseTime(String value, String file, String recordId) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(422, "INVALID_CSV_VALUE",
                    file + " has an invalid time for record " + recordId + ".");
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String file, String recordId) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(422, "INVALID_CSV_VALUE",
                    file + " has an invalid " + enumType.getSimpleName() + " for record " + recordId + ".");
        }
    }

    private void requireValue(Map<String, String> row, String column, String file, String recordId) {
        if (value(row, column).isBlank()) {
            throw new ApiException(422, "INVALID_CSV_VALUE",
                    file + " has an empty " + column + " for record " + recordId + ".");
        }
    }

    private boolean reportIsSafe(Path directory, Path file) throws IOException {
        if (!Files.isDirectory(directory) || !Files.isRegularFile(file) || !Files.isReadable(file)) return false;
        Path realDirectory = directory.toRealPath();
        return file.toRealPath().startsWith(realDirectory);
    }

    private record Report(String id, String name, String fileName, String note) {}

    private record RunSnapshot(PassportSlotEngine engine, ExecutionSummary summary, LocalDateTime asOf,
                               Path outputDirectory, LocalDateTime completedAt) {}
}
