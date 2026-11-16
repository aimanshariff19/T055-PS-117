package com.ps117.lifecycle;

import com.ps117.domain.Appointment;
import com.ps117.domain.RescheduleHistory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RescheduleService {
    private final List<RescheduleHistory> history = new ArrayList<>();

    public boolean reschedule(Appointment appointment, String previousSlotId, String newSlotId, String reason) {
        if (appointment == null) return false;
        RescheduleHistory item = new RescheduleHistory();
        item.setAppointmentId(appointment.getAppointmentId());
        item.setPreviousSlotId(previousSlotId);
        item.setNewSlotId(newSlotId);
        item.setRescheduleTimestamp(LocalDateTime.now());
        item.setReason(reason);
        history.add(item);
        appointment.setSlotId(newSlotId);
        return true;
    }

    public List<RescheduleHistory> getHistory() {
        return history;
    }
}
