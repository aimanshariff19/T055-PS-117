package com.ps117.reporting;

import com.ps117.domain.Appointment;

import java.util.List;

public class CentreDaySheetGenerator {
    public String generate(String centreId, List<Appointment> appointments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Centre,Date,Appointments\n");
        for (Appointment appointment : appointments) {
            sb.append(centreId).append(',').append(appointment.getAppointmentDate()).append(',').append(appointment.getAppointmentId()).append('\n');
        }
        return sb.toString();
    }
}
