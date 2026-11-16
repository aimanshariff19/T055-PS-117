package com.ps117.allocation;

import com.ps117.domain.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SlotManager {
    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();

    public void addSlot(Slot slot) {
        slots.put(slot.getSlotId(), slot);
    }

    public List<Slot> getAllSlots() {
        return new ArrayList<>(slots.values());
    }

    public Slot findAvailableSlot(String centreId, java.time.LocalDate date) {
        return slots.values().stream()
                .filter(s -> s.getCentreId().equals(centreId) && s.getDate().equals(date) && s.getAvailableCapacity() > 0)
                .sorted(Comparator.comparing(Slot::getStartTime))
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean reserve(String slotId) {
        Slot slot = slots.get(slotId);
        if (slot == null || slot.getAvailableCapacity() <= 0) {
            return false;
        }
        slot.allocate();
        return true;
    }
}
