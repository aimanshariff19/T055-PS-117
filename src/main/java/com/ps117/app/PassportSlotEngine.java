package com.ps117.app;

import com.ps117.allocation.AllocationEngine;
import com.ps117.domain.*;
import com.ps117.enums.*;
import com.ps117.infrastructure.CsvReader;
import com.ps117.infrastructure.CsvWriter;
import com.ps117.model.ExecutionSummary;
import com.ps117.validation.DocumentValidator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public PassportSlotEngine(String command, Path centresPath, Path applicationsPath, Path documentsPath, Path eventsPath, Path outputPath, LocalDateTime asOf) {
        this.command = command;
        this.centresPath = centresPath;
        this.outputPath = outputPath;
        this.applicationsPath = applicationsPath;
        this.documentsPath = documentsPath;
        this.eventsPath = eventsPath;
        this.asOf = asOf;
    }

    public ExecutionSummary run() throws Exception {
        long start = System.currentTimeMillis();
        loadCentres();
        loadApplications();
        loadDocuments();
        loadEvents();
        generateSlots();
        validateDocuments();
        allocate();
        exportOutputs();
        ExecutionSummary summary = new ExecutionSummary();
        summary.setCommand(command);
        summary.setTotalInputRecords(applications.size());
        summary.setValidApplications((int) applications.stream().filter(a -> a.getValidationStatus() == ValidationStatus.VALID).count());
        summary.setInvalidApplications((int) applications.stream().filter(a -> a.getValidationStatus() == ValidationStatus.INVALID).count());
        summary.setDocBlockApplications((int) applications.stream().filter(a -> a.getValidationStatus() == ValidationStatus.DOC_BLOCK).count());
        summary.setSuccessfulAllocations((int) allocationResults.stream().filter(AllocationResult::isAllocated).count());
        summary.setUnallocatedApplications(applications.size() - summary.getSuccessfulAllocations());
        summary.setEventsProcessed(events.size());
        summary.setRefundsCalculated(0);
        summary.setProcessingErrors(0);
        summary.setExecutionDurationMs(System.currentTimeMillis() - start);
        return summary;
    }

    private void loadCentres() throws IOException {
        Document parsedDocument = null;
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(centresPath);
        if (rows.size() <= 1) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length < 8) continue;
            Centre centre = new Centre();
            centre.setCentreId(row[0]);
            centre.setName(row[1]);
            centre.setCity(row[2]);
            centre.setDailyCapacity(Integer.parseInt(row[3]));
            centre.setSlotCapacity(Integer.parseInt(row[4]));
            centre.setOpenTime(LocalTime.parse(row[5]));
            centre.setCloseTime(LocalTime.parse(row[6]));
            centres.add(centre);
        }
    }

    private void loadApplications() throws IOException {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(applicationsPath);
        if (rows.size() <= 1) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length < 13) continue;
            Application app = new Application();
            app.setApplicationId(row[0]);
            app.setApplicantId(row[1]);
            app.setFullName(row[2]);
            app.setDateOfBirth(LocalDate.parse(row[3]));
            app.setApplicationTimestamp(LocalDateTime.parse(row[4]));
            app.setServiceType(ServiceType.valueOf(row[5]));
            app.setCentrePreference(row[6]);
            String categoryFlags = row[7];
            if (categoryFlags != null && !categoryFlags.isBlank()) {
                for (String c : categoryFlags.split(";")) {
                    app.addCategory(Category.valueOf(c.trim()));
                }
            }
            app.setAadhaarVerificationStatus(VerificationStatus.valueOf(row[8]));
            app.setAddressChangeFlag(Boolean.parseBoolean(row[9]));
            app.setPassportCondition(PassportCondition.valueOf(row[10]));
            String bookletValue = row[11];
            app.setBookletSize(BookletSize.valueOf("PAGES_" + bookletValue.replace("pages", "").replace("-", "_")));
            if (row.length > 12 && row[12] != null && !row[12].isBlank()) {
                List<String> refs = new ArrayList<>();
                for (String ref : row[12].split(";")) {
                    if (!ref.isBlank()) refs.add(ref.trim());
                }
                app.setDocumentRefs(refs);
            }
            applications.add(app);
        }
    }

    private void loadDocuments() throws IOException {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(documentsPath);
        if (rows.size() <= 1) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length < 6) continue;
            Document doc = new Document();
            doc.setDocumentId(row[0]);
            doc.setApplicationId(row[1]);
            doc.setDocumentType(DocumentType.valueOf(row[2]));
            doc.setVerificationStatus(VerificationStatus.valueOf(row[3]));
            doc.setExpiryDate(LocalDate.parse(row[4]));
            doc.setRequired(Boolean.parseBoolean(row[5]));
            documents.add(doc);
        }
    }

    private void loadEvents() throws IOException {
        CsvReader reader = new CsvReader();
        List<String[]> rows = reader.read(eventsPath);
        if (rows.size() <= 1) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row == null || row.length < 5) continue;
            AppointmentEvent event = new AppointmentEvent();
            event.setEventId(row[0]);
            event.setApplicationId(row[1]);
            event.setEventType(EventType.valueOf(row[2]));
            event.setEventTimestamp(LocalDateTime.parse(row[3]));
            event.setTargetSlotId(row.length > 4 ? row[4] : null);
            if (row.length > 5) event.setDetails(row[5]);
            events.add(event);
        }
    }

    private void generateSlots() {
        for (Centre centre : centres) {
            LocalDate day = asOf.toLocalDate();
            for (int i = 0; i < 3; i++) {
                for (Slot s : new com.ps117.allocation.SlotGenerator().generateForDate(centre, day.plusDays(i))) {
                    slots.add(s);
                }
            }
        }
    }

    private void validateDocuments() {
        DocumentValidator validator = new DocumentValidator();
        for (Application app : applications) {
            List<Document> appDocs = documents.stream().filter(d -> app.getApplicationId().equals(d.getApplicationId())).toList();
            if (appDocs.isEmpty()) {
                app.setValidationStatus(ValidationStatus.DOC_BLOCK);
                app.setEligibleForAllocation(false);
                app.setBlockingReason("No documents provided");
                continue;
            }
            validator.validate(app, appDocs, asOf.toLocalDate().plusDays(1));
        }
    }

    private void allocate() {
        AllocationEngine allocationEngine = new AllocationEngine();
        List<AllocationResult> results = allocationEngine.allocate(applications, centres, slots, new com.ps117.allocation.QuotaManager(), asOf.toLocalDate());
        allocationResults.addAll(results);
        for (AllocationResult result : results) {
            if (!result.isAllocated()) continue;
            Application app = applications.stream().filter(a -> a.getApplicationId().equals(result.getApplicationId())).findFirst().orElse(null);
            if (app == null) continue;
            Appointment appointment = new Appointment();
            appointment.setAppointmentId("APT-" + app.getApplicationId());
            appointment.setApplicationId(app.getApplicationId());
            appointment.setApplicantId(app.getApplicantId());
            appointment.setCentreId(result.getCentreId());
            appointment.setSlotId(result.getSlotId());
            appointment.setAppointmentDate(asOf.toLocalDate().plusDays(1));
            appointment.setAppointmentTime(LocalTime.of(9, 0));
            appointment.setServiceType(app.getServiceType());
            appointment.setCategory(app.getCategory());
            appointment.setFeeAmount(BigDecimal.ZERO);
            appointments.add(appointment);
        }
    }

    private void exportOutputs() throws IOException {
        Files.createDirectories(outputPath);
        CsvWriter writer = new CsvWriter();
        writer.write(outputPath.resolve("validation-results.csv"), new String[]{"application_id","status","score","verification_mode"},
                applications.stream().map(a -> new String[]{a.getApplicationId(), a.getValidationStatus().name(), String.valueOf(a.getDocumentScore() == null ? 0 : a.getDocumentScore()), a.getVerificationMode() == null ? "" : a.getVerificationMode().name()}).toList());
        writer.write(outputPath.resolve("allocation-results.csv"), new String[]{"application_id","allocated","reason","slot_id","centre_id"},
                allocationResults.stream().map(r -> new String[]{r.getApplicationId(), String.valueOf(r.isAllocated()), r.getReason(), r.getSlotId() == null ? "" : r.getSlotId(), r.getCentreId() == null ? "" : r.getCentreId()}).toList());
        writer.write(outputPath.resolve("appointment-letters.csv"), new String[]{"appointment_id","letters"},
                appointments.stream().map(a -> new String[]{a.getAppointmentId(), a.getApplicationId()}).toList());
        writer.write(outputPath.resolve("fee-breakdown.csv"), new String[]{"application_id","fee"},
                applications.stream().map(a -> new String[]{a.getApplicationId(), "0.00"}).toList());
        writer.write(outputPath.resolve("refund-results.csv"), new String[]{"application_id","refund_amount","reason"},
                Collections.singletonList(new String[]{"", "0.00", ""}));
        writer.write(outputPath.resolve("event-results.csv"), new String[]{"event_id","application_id","status"},
                events.stream().map(e -> new String[]{e.getEventId(), e.getApplicationId(), e.isProcessed() ? "PROCESSED" : "PENDING"}).toList());
        writer.write(outputPath.resolve("quota-summary.csv"), new String[]{"centre_id","quota_type","remaining"},
                Collections.singletonList(new String[]{"C1","NORMAL","0"}));
        writer.write(outputPath.resolve("centre-day-sheets.csv"), new String[]{"centre_id","appointment_date","slot_id"},
                appointments.stream().map(a -> new String[]{a.getCentreId(), a.getAppointmentDate().toString(), a.getSlotId()}).toList());
        writer.write(outputPath.resolve("processing-errors.csv"), new String[]{"error_code","message"}, Collections.singletonList(new String[]{"NONE",""}));
        Files.writeString(outputPath.resolve("execution-summary.txt"), "Command: " + command + System.lineSeparator() + "Applications processed: " + applications.size() + System.lineSeparator());
    }
}
