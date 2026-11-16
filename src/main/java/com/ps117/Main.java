package com.ps117;

import com.ps117.app.PassportSlotEngine;
import com.ps117.cli.CommandProcessor;
import com.ps117.cli.ConsoleReporter;
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
        System.out.println("Options: --command COMMAND --centres FILE --applications FILE");
        System.out.println("         --documents FILE --events FILE --output DIRECTORY --as-of ISO_DATE_TIME");
        System.out.println("         --help, --version");
        System.out.println("Note: command selectors currently invoke the same processing pipeline.");
    }
}
