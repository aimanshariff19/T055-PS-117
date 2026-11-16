package com.ps117.allocation;

import com.ps117.domain.AllocationResult;
import com.ps117.domain.Application;
import com.ps117.domain.Centre;
import com.ps117.domain.Slot;
import com.ps117.enums.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllocationEngineTest {
    @Test
    void allocatesApplicationToEarliestAvailableSlot() {
        Centre centre = new Centre("C1", "Central", "City", 50, 10, LocalTime.of(9, 0), LocalTime.of(16, 0));
        Application app = new Application();
        app.setApplicationId("A1");
        app.setServiceType(ServiceType.NORMAL);
        app.setCategory(Category.NORMAL);
        app.setCentrePreference("C1");
        app.setApplicationTimestamp(java.time.LocalDateTime.of(2026, 11, 10, 8, 0));
        app.setValidationStatus(ValidationStatus.VALID);
        app.setEligibleForAllocation(true);

        Slot slot = new Slot("S1", "C1", LocalDate.of(2026, 11, 11), LocalTime.of(9, 0), 10);
        AllocationEngine engine = new AllocationEngine();
        List<AllocationResult> results = engine.allocate(List.of(app), List.of(centre), List.of(slot), new QuotaManager(), LocalDate.of(2026, 11, 10));

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).isAllocated());
    }
}
