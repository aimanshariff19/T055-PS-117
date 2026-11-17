package com.ps117.lifecycle;

import com.ps117.domain.Application;
import com.ps117.domain.Appointment;
import com.ps117.enums.ApplicationStatus;
import com.ps117.enums.AppointmentStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NoShowService {
    private final Set<String> processedNoShowEvents = new HashSet<>();
    private final Map<String, Integer> applicantNoShowCounts = new HashMap<>();

    public boolean recordNoShow(Appointment appointment) {
        return recordNoShow(null, appointment);
    }

    public boolean recordNoShow(Application application, Appointment appointment) {
        if (appointment == null) return false;
        if (!processedNoShowEvents.add(appointment.getAppointmentId())) {
            return false;
        }

        appointment.setStatus(AppointmentStatus.EXPIRED);

        String applicantKey = application != null ? application.getApplicantId() : appointment.getApplicantId();
        if (applicantKey != null) {
            int count = applicantNoShowCounts.merge(applicantKey, 1, Integer::sum);
            if (count >= 2 && application != null) {
                application.setApplicationStatus(ApplicationStatus.REJECTED);
                application.setEligibleForAllocation(false);
                application.setBlockingReason("Second no-show: fee forfeited, rebooking barred");
            }
        }
        return true;
    }

    public int getNoShowCount(String applicantId) {
        return applicantNoShowCounts.getOrDefault(applicantId, 0);
    }
}
