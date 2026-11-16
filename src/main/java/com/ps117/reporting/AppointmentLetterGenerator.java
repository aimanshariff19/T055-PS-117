package com.ps117.reporting;

import com.ps117.domain.Appointment;

public class AppointmentLetterGenerator {
    public String generate(Appointment appointment) {
        if (appointment == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Appointment ID: ").append(appointment.getAppointmentId()).append(System.lineSeparator());
        sb.append("Applicant: ").append(appointment.getApplicantId()).append(System.lineSeparator());
        sb.append("Centre: ").append(appointment.getCentreId()).append(System.lineSeparator());
        sb.append("Date: ").append(appointment.getAppointmentDate()).append(System.lineSeparator());
        sb.append("Time: ").append(appointment.getAppointmentTime()).append(System.lineSeparator());
        sb.append("Service: ").append(appointment.getServiceType()).append(System.lineSeparator());
        sb.append("Instructions: Arrive 30 minutes early and carry all required documents.");
        return sb.toString();
    }
}
