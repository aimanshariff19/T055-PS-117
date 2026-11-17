package com.ps117.allocation;

import com.ps117.domain.Application;
import com.ps117.domain.Centre;
import com.ps117.enums.ServiceType;
import com.ps117.util.DateUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class BookingWindowValidator {
    private final Set<LocalDate> holidays = new HashSet<>();

    public BookingWindowValidator() {
        // Standard official holidays from holidays.csv
        holidays.add(LocalDate.of(2026, 10, 2));
        holidays.add(LocalDate.of(2026, 10, 20));
        holidays.add(LocalDate.of(2026, 10, 21));
    }

    public void addHoliday(LocalDate holiday) {
        if (holiday != null) {
            holidays.add(holiday);
        }
    }

    public boolean isEligible(Application application, Centre centre, LocalDate appointmentDate, LocalDate asOfDate) {
        if (application == null || centre == null || appointmentDate == null || asOfDate == null) {
            return false;
        }

        LocalDate earliestWorkingDay = DateUtils.nextWorkingDay(asOfDate, holidays);
        if (appointmentDate.isBefore(earliestWorkingDay)) {
            return false;
        }

        ServiceType service = application.getServiceType();
        if (service == ServiceType.TATKAL) {
            LocalDate maxDate = earliestWorkingDay;
            int daysCount = 0;
            while (daysCount < 2) {
                maxDate = DateUtils.nextWorkingDay(maxDate, holidays);
                daysCount++;
            }
            LocalDate latestTatkal = asOfDate.plusDays(3);
            LocalDate latestAllowed = latestTatkal.isAfter(maxDate) ? latestTatkal : maxDate;
            return !appointmentDate.isAfter(latestAllowed);
        }

        if (service == ServiceType.PCC) {
            return !appointmentDate.isAfter(asOfDate.plusDays(30));
        }

        if (service == ServiceType.NORMAL || service == ServiceType.RE_ISSUE) {
            return !appointmentDate.isAfter(asOfDate.plusDays(45));
        }

        return !appointmentDate.isAfter(asOfDate.plusDays(45));
    }

    public int computeEarliestDay(LocalDate asOfDate) {
        LocalDate candidate = DateUtils.nextWorkingDay(asOfDate, holidays);
        return candidate.compareTo(asOfDate);
    }

    public Set<LocalDate> getHolidays() {
        return holidays;
    }
}
