package com.ps117.lifecycle;

import com.ps117.domain.Application;
import com.ps117.domain.Appointment;
import com.ps117.domain.RescheduleHistory;
import com.ps117.enums.AppointmentStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RescheduleService {
    public static final int MAX_RESCHEDULES = 3;
    private final List<RescheduleHistory> history = new ArrayList<>();
    private final Map<String, LocalDateTime> lastRescheduleMap = new HashMap<>();

    public boolean canReschedule(Application application, LocalDateTime requestTime) {
        if (application == null) return false;
        if (application.getRescheduleCount() >= MAX_RESCHEDULES) {
            return false;
        }
        LocalDateTime lastTime = lastRescheduleMap.get(application.getApplicationId());
        if (lastTime != null && requestTime != null) {
            long days = Duration.between(lastTime, requestTime).toDays();
            if (days < 7) {
                return false;
            }
        }
        return true;
    }

    public boolean reschedule(Application application, Appointment appointment, String previousSlotId, String newSlotId, LocalDateTime requestTime, String reason) {
        if (application == null || appointment == null) return false;
        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.EXPIRED) {
            return false;
        }

        if (!canReschedule(application, requestTime)) {
            return false;
        }

        int count = application.getRescheduleCount() + 1;
        application.setRescheduleCount(count);
        if (count == MAX_RESCHEDULES) {
            application.setThirdRescheduleMovedToBack(true);
        }

        if (requestTime != null) {
            lastRescheduleMap.put(application.getApplicationId(), requestTime);
        }

        RescheduleHistory item = new RescheduleHistory();
        item.setAppointmentId(appointment.getAppointmentId());
        item.setPreviousSlotId(previousSlotId);
        item.setNewSlotId(newSlotId);
        item.setRescheduleTimestamp(requestTime != null ? requestTime : LocalDateTime.now());
        item.setReason(reason);
        history.add(item);

        appointment.setSlotId(newSlotId);
        appointment.setStatus(AppointmentStatus.RESCHEDULED);
        return true;
    }

    public boolean reschedule(Appointment appointment, String previousSlotId, String newSlotId, String reason) {
        return reschedule(null, appointment, previousSlotId, newSlotId, LocalDateTime.now(), reason);
    }

    public List<RescheduleHistory> getHistory() {
        return history;
    }
}
