package com.ps117.lifecycle;

import com.ps117.domain.Appointment;
import com.ps117.enums.AppointmentStatus;

import java.util.HashSet;
import java.util.Set;

public class NoShowService {
    private final Set<String> processedNoShowEvents = new HashSet<>();

    public boolean recordNoShow(Appointment appointment) {
        if (appointment == null) return false;
        if (!processedNoShowEvents.add(appointment.getAppointmentId())) {
            return false;
        }
        appointment.setStatus(AppointmentStatus.EXPIRED);
        return true;
    }
}
