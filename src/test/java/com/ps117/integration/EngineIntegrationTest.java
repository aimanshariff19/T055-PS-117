package com.ps117.integration;

import com.ps117.app.PassportSlotEngine;
import com.ps117.model.ExecutionSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EngineIntegrationTest {
    @Test
    void runsBasicProcessingFlow(@TempDir Path tempDirectory) throws Exception {
        PassportSlotEngine engine = new PassportSlotEngine(
                "PROCESS",
                Path.of("T055-PS-117-sample-data/centres.csv"),
                Path.of("T055-PS-117-sample-data/applications.csv"),
                Path.of("T055-PS-117-sample-data/documents.csv"),
                null,
                tempDirectory,
                LocalDateTime.of(2026, 11, 15, 17, 0)
        );

        ExecutionSummary summary = engine.run();
        assertNotNull(summary);
        assertTrue(summary.getTotalInputRecords() >= 1);
        assertTrue(summary.getExecutionDurationMs() >= 0);
    }
}
