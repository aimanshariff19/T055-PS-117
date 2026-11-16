package com.ps117.allocation;

import com.ps117.domain.Application;
import com.ps117.domain.Centre;
import com.ps117.enums.ServiceType;
import com.ps117.util.DateUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class BookingWindowValidator {
    private final Set<LocalDate> holidays = new HashSet<>();

    public boolean isEligible(Application application, Centre centre, LocalDate appointmentDate, LocalDate asOfDate) {
        if (application == null || centre == null || appointmentDate == null || asOfDate == null) {
            return false;
        }
        if (application.getServiceType() == ServiceType.NORMAL) {
            return !appointmentDate.isBefore(asOfDate.plusDays(1)) && !appointmentDate.isAfter(asOfDate.plusDays(45));
        }
        if (application.getServiceType() == ServiceType.TATKAL) {
            LocalDate earliest = asOfDate.plusDays(1);
            LocalDate latest = asOfDate.plusDays(3);
            return !appointmentDate.isBefore(earliest) && !appointmentDate.isAfter(latest);
        }
        if (application.getServiceType() == ServiceType.PCC) {
            return !appointmentDate.isBefore(asOfDate.plusDays(1)) && !appointmentDate.isAfter(asOfDate.plusDays(30));
        }
        if (application.getServiceType() == ServiceType.RE_ISSUE) {
            return !appointmentDate.isBefore(asOfDate.plusDays(1)) && !appointmentDate.isAfter(asOfDate.plusDays(45));
        }
        return false;
    }

    public int computeEarliestDay(LocalDate asOfDate) {
        LocalDate candidate = asOfDate.plusDays(1);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.compareTo(asOfDate);
    }

    public Set<LocalDate> getHolidays() {
        return holidays;
    }
}
