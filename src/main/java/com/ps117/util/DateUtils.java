package com.ps117.util;

import java.time.*;
import java.util.Set;

public final class DateUtils {
    private DateUtils() {}

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    public static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDateTime.parse(value);
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static boolean isHoliday(LocalDate date, Set<LocalDate> holidays) {
        return holidays != null && holidays.contains(date);
    }

    public static LocalDate nextWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        LocalDate current = date;
        while (true) {
            current = current.plusDays(1);
            if (!isWeekend(current) && !isHoliday(current, holidays)) {
                return current;
            }
        }
    }

    public static int workingDaysBetween(LocalDate startInclusive, LocalDate endExclusive, Set<LocalDate> holidays) {
        int count = 0;
        for (LocalDate day = startInclusive; day.isBefore(endExclusive); day = day.plusDays(1)) {
            if (!isWeekend(day) && !isHoliday(day, holidays)) {
                count++;
            }
        }
        return count;
    }
}
