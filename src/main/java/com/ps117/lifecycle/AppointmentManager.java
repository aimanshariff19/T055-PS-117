package com.ps117.lifecycle;

import com.ps117.domain.Appointment;
import com.ps117.enums.AppointmentStatus;

import java.util.ArrayList;
import java.util.List;

public class AppointmentManager {
    private final List<Appointment> appointments = new ArrayList<>();

    public void addAppointment(Appointment appointment) {
        appointments.add(appointment);
    }

    public List<Appointment> getAppointments() {
        return appointments;
    }

    public Appointment findByApplicationId(String applicationId) {
        return appointments.stream()
                .filter(a -> a.getApplicationId().equals(applicationId))
                .findFirst()
                .orElse(null);
    }

    public void updateStatus(String applicationId, AppointmentStatus status) {
        Appointment appointment = findByApplicationId(applicationId);
        if (appointment != null) {
            appointment.setStatus(status);
        }
    }
}
