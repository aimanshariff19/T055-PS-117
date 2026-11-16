package com.ps117.cli;

import com.ps117.infrastructure.ApplicationConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandProcessorTest {
    @Test
    void loadsDefaultsAndParsesCommand() throws IOException {
        ApplicationConfig config = ApplicationConfig.load();
        CommandProcessor processor = new CommandProcessor(new String[]{"--command", "ALLOCATE"}, config);

        assertEquals("ALLOCATE", processor.getCommand());
        assertEquals(config.defaultCentresPath(), processor.getCentresPath());
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
