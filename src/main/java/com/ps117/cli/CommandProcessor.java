package com.ps117.cli;

import com.ps117.app.PassportSlotEngine;
import com.ps117.infrastructure.ApplicationConfig;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

public class CommandProcessor {
    private static final Set<String> COMMANDS = Set.of(
            "PROCESS", "VALIDATE", "ALLOCATE", "QUOTA-REPORT",
            "FEE-REPORT", "DAY-SHEET", "EVENT-PROCESS"
    );

    private String command = "PROCESS";
    private Path centresPath;
    private Path applicationsPath;
    private Path documentsPath;
    private Path eventsPath;
    private Path inputDirectory;
    private Path outputPath;
    private LocalDateTime asOf;
    private boolean helpRequested;
    private boolean versionRequested;

    public CommandProcessor(String[] args, ApplicationConfig config) {
        centresPath = config.defaultCentresPath();
        applicationsPath = config.defaultApplicationsPath();
        documentsPath = config.defaultDocumentsPath();
        eventsPath = config.defaultEventsPath();
        outputPath = config.defaultOutputPath();
        parse(args);
    }

    private void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--command" -> command = commandValue(valueAfter(args, ++i, "--command"));
                case "--input-dir" -> setInputDirectory(Path.of(valueAfter(args, ++i, "--input-dir")));
                case "--centres" -> centresPath = Path.of(valueAfter(args, ++i, "--centres"));
                case "--applications" -> applicationsPath = Path.of(valueAfter(args, ++i, "--applications"));
                case "--documents" -> documentsPath = Path.of(valueAfter(args, ++i, "--documents"));
                case "--events" -> eventsPath = Path.of(valueAfter(args, ++i, "--events"));
                case "--output" -> outputPath = Path.of(valueAfter(args, ++i, "--output"));
                case "--as-of" -> asOf = parseAsOf(valueAfter(args, ++i, "--as-of"));
                case "--help", "-h" -> helpRequested = true;
                case "--version" -> versionRequested = true;
                default -> {
                    if (COMMANDS.contains(args[i].toUpperCase())) {
                        command = args[i].toUpperCase();
                    } else {
                        throw new IllegalArgumentException("Unknown argument or command: " + args[i]);
                    }
                }
            }
        }
        if (asOf == null) asOf = LocalDateTime.now();
    }

    private String valueAfter(String[] args, int valueIndex, String option) {
        if (valueIndex >= args.length || args[valueIndex].isBlank() || args[valueIndex].startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[valueIndex];
    }

    private String commandValue(String value) {
        String normalized = value.toUpperCase();
        if (!COMMANDS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported command: " + value);
        }
        return normalized;
    }

    private void setInputDirectory(Path inputDirectory) {
        this.inputDirectory = inputDirectory;
        centresPath = inputDirectory.resolve("centres.csv");
        applicationsPath = inputDirectory.resolve("applications.csv");
        documentsPath = inputDirectory.resolve("documents.csv");
        eventsPath = inputDirectory.resolve("events.csv");
    }

    private LocalDateTime parseAsOf(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("--as-of must be an ISO local date-time, for example 2026-11-16T09:00:00", exception);
        }
    }

    public PassportSlotEngine createEngine() {
        return new PassportSlotEngine(command, centresPath, applicationsPath, documentsPath, eventsPath, outputPath, asOf);
    }

    public boolean isHelpRequested() { return helpRequested; }
    public boolean isVersionRequested() { return versionRequested; }
    public String getCommand() { return command; }
    public Path getInputDirectory() { return inputDirectory; }
    public Path getCentresPath() { return centresPath; }
    public Path getApplicationsPath() { return applicationsPath; }
    public Path getDocumentsPath() { return documentsPath; }
    public Path getEventsPath() { return eventsPath; }
    public Path getOutputPath() { return outputPath; }
    public LocalDateTime getAsOf() { return asOf; }
}
