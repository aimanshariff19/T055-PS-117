package com.ps117.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

public record ApplicationConfig(
        String applicationName,
        String applicationVersion,
        String commands,
        Path defaultCentresPath,
        Path defaultApplicationsPath,
        Path defaultDocumentsPath,
        Path defaultEventsPath,
        Path defaultOutputPath
) {
    public static ApplicationConfig load() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = ApplicationConfig.class.getResourceAsStream("/application.properties")) {
            if (stream == null) {
                throw new IOException("Missing application configuration resource: application.properties");
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        return new ApplicationConfig(
                required(properties, "application.name"),
                required(properties, "application.version"),
                required(properties, "application.commands"),
                Path.of(required(properties, "paths.centres")),
                Path.of(required(properties, "paths.applications")),
                Path.of(required(properties, "paths.documents")),
                Path.of(required(properties, "paths.events")),
                Path.of(required(properties, "paths.output"))
        );
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required application configuration: " + key);
        }
        return value.trim();
    }
}
