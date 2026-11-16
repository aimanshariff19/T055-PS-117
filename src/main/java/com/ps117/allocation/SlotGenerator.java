package com.ps117.allocation;

import com.ps117.domain.Centre;
import com.ps117.domain.Slot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class SlotGenerator {
    public List<Slot> generateForDate(Centre centre, LocalDate date) {
        List<Slot> slots = new ArrayList<>();
        LocalTime time = centre.getOpenTime();
        while (!time.equals(centre.getCloseTime())) {
            String slotId = centre.getCentreId() + "-" + date + "-" + time;
            slots.add(new Slot(slotId, centre.getCentreId(), date, time, centre.getSlotCapacity()));
            time = time.plusMinutes(15);
        }
        return slots;
    }
}
