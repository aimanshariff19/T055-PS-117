package com.ps117.cli;

import com.ps117.infrastructure.ApplicationConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandProcessorTest {
    @Test
    void loadsDefaultsAndParsesCommand() throws IOException {
        ApplicationConfig config = ApplicationConfig.load();
        CommandProcessor processor = new CommandProcessor(new String[]{"--command", "ALLOCATE"}, config);

        assertEquals("ALLOCATE", processor.getCommand());
        assertNull(processor.getCentresPath());
        assertNull(processor.getInputDirectory());
    }

    @Test
    void inputDirectorySetsBatchInputFiles() throws IOException {
        ApplicationConfig config = ApplicationConfig.load();
        Path inputDirectory = Path.of("batch-inputs");
        CommandProcessor processor = new CommandProcessor(
                new String[]{"--input-dir", inputDirectory.toString()}, config);

        assertEquals(inputDirectory, processor.getInputDirectory());
        assertEquals(inputDirectory.resolve("centres.csv"), processor.getCentresPath());
        assertEquals(inputDirectory.resolve("applications.csv"), processor.getApplicationsPath());
        assertEquals(inputDirectory.resolve("documents.csv"), processor.getDocumentsPath());
        assertEquals(inputDirectory.resolve("events.csv"), processor.getEventsPath());
    }

    @Test
    void rejectsUnknownArguments() throws IOException {
        ApplicationConfig config = ApplicationConfig.load();

        assertThrows(IllegalArgumentException.class,
                () -> new CommandProcessor(new String[]{"--unknown"}, config));
    }

    @Test
    void rejectsMissingOptionValues() throws IOException {
        ApplicationConfig config = ApplicationConfig.load();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CommandProcessor(new String[]{"--centres"}, config));
        assertTrue(error.getMessage().contains("Missing value"));
    }
}
