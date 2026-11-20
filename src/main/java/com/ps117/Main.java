package com.ps117;

import com.ps117.app.PassportSlotEngine;
import com.ps117.cli.CommandProcessor;
import com.ps117.cli.ConsoleReporter;
import com.ps117.engine.CommandStreamEngine;
import com.ps117.infrastructure.ApplicationConfig;
import com.ps117.model.ExecutionSummary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) throws Exception {
        int javaFeatureVersion = Runtime.version().feature();
        if (javaFeatureVersion < 17) {
            System.err.println("PS-117 requires Java 17 or newer. Detected Java " + javaFeatureVersion + ".");
            return 1;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("STREAM")) {
            return runStream(args);
        }

        ApplicationConfig config = ApplicationConfig.load();
        System.out.println(config.applicationName());
        System.out.println("Version: " + config.applicationVersion());
        System.out.println("Java runtime: " + Runtime.version());
        System.out.println("Configuration: loaded");

        CommandProcessor processor;
        try {
            processor = new CommandProcessor(args, config);
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid arguments: " + exception.getMessage());
            printUsage(config);
            return 2;
        }

        if (processor.isVersionRequested()) {
            return 0;
        }
        if (processor.isHelpRequested()) {
            printUsage(config);
            return 0;
        }

        List<Path> inputFiles = List.of(
                processor.getCentresPath(),
                processor.getApplicationsPath(),
                processor.getDocumentsPath(),
                processor.getEventsPath()
        );
        List<Path> missingFiles = inputFiles.stream()
                .filter(path -> !Files.isRegularFile(path) || !Files.isReadable(path))
                .toList();
        if (!missingFiles.isEmpty()) {
            System.err.println("Input file(s) are missing or unreadable:");
            missingFiles.forEach(path -> System.err.println("  " + path));
            printUsage(config);
            return 2;
        }

        PassportSlotEngine engine = processor.createEngine();
        ExecutionSummary summary = engine.run();
        ConsoleReporter.printSummary(summary);
        return 0;
    }

    private static void printUsage(ApplicationConfig config) {
        System.out.println("Usage: java -jar target/T055-PS-117-1.0.0.jar [COMMAND] [OPTIONS]");
        System.out.println("Commands: " + config.commands());
        System.out.println("          STREAM (command-stream engine)");
        System.out.println("Options: --command COMMAND --centres FILE --applications FILE");
        System.out.println("         --documents FILE --events FILE --output DIRECTORY --as-of ISO_DATE_TIME");
        System.out.println("         --help, --version");
        System.out.println("STREAM Options: --commands FILE --centres FILE --applications FILE");
        System.out.println("         --documents FILE --holidays FILE --slot-capacity FILE --output DIRECTORY");
    }

    /**
     * Handles STREAM and validates all required input paths before loading data.
     */
    private static int runStream(String[] args) throws Exception {
        Path commandsPath = null;
        Path centresPath = null;
        Path applicationsPath = null;
        Path documentsPath = null;
        Path holidaysPath = null;
        Path slotCapacityPath = null;
        Path outputPath = Path.of("output");

        for (int i = 1; i < args.length; i++) {
            String option = args[i];
            if (i + 1 >= args.length || args[i + 1].isBlank() || args[i + 1].startsWith("--")) {
                System.err.println("STREAM option requires a value: " + option);
                return 2;
            }

            Path value = Path.of(args[++i]);
            switch (option) {
                case "--commands" -> commandsPath = value;
                case "--centres" -> centresPath = value;
                case "--applications" -> applicationsPath = value;
                case "--documents" -> documentsPath = value;
                case "--holidays" -> holidaysPath = value;
                case "--slot-capacity" -> slotCapacityPath = value;
                case "--output" -> outputPath = value;
                default -> {
                    System.err.println("Unknown STREAM option: " + option);
                    return 2;
                }
            }
        }

        if (commandsPath == null || centresPath == null || applicationsPath == null
                || documentsPath == null || holidaysPath == null || slotCapacityPath == null) {
            System.err.println("STREAM requires --commands, --centres, --applications, --documents, --holidays, and --slot-capacity.");
            return 2;
        }

        List<Path> inputFiles = List.of(commandsPath, centresPath, applicationsPath, documentsPath, holidaysPath, slotCapacityPath);
        List<Path> missingFiles = inputFiles.stream()
                .filter(path -> !Files.isRegularFile(path) || !Files.isReadable(path))
                .toList();
        if (!missingFiles.isEmpty()) {
            System.err.println("STREAM input file(s) are missing or unreadable:");
            missingFiles.forEach(path -> System.err.println("  " + path));
            return 2;
        }

        System.out.println("[STREAM] Loading data and processing commands: " + commandsPath);
        CommandStreamEngine engine = new CommandStreamEngine(
                centresPath, applicationsPath, documentsPath, holidaysPath, slotCapacityPath);
        engine.processCommandFile(commandsPath, outputPath);
        System.out.println("========================================");
        System.out.println("[STREAM] Done. Output written to: " + outputPath.toAbsolutePath());
        return 0;
    }
}
