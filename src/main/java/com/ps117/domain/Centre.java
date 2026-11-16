package com.ps117.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Centre {
    private String centreId;
    private String name;
    private String city;
    private int dailyCapacity;
    private int slotCapacity;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Set<DayOfWeek> workingDays = new HashSet<>();

    public Centre() {}

    public Centre(String centreId, String name, String city, int dailyCapacity, int slotCapacity, LocalTime openTime, LocalTime closeTime) {
        this.centreId = centreId;
        this.name = name;
        this.city = city;
        this.dailyCapacity = dailyCapacity;
        this.slotCapacity = slotCapacity;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public String getCentreId() { return centreId; }
    public void setCentreId(String centreId) { this.centreId = centreId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public int getDailyCapacity() { return dailyCapacity; }
    public void setDailyCapacity(int dailyCapacity) { this.dailyCapacity = dailyCapacity; }
    public int getSlotCapacity() { return slotCapacity; }
    public void setSlotCapacity(int slotCapacity) { this.slotCapacity = slotCapacity; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public Set<DayOfWeek> getWorkingDays() { return workingDays; }
    public void setWorkingDays(Set<DayOfWeek> workingDays) { this.workingDays = workingDays == null ? new HashSet<>() : workingDays; }

    public boolean isWorkingDay(java.time.DayOfWeek dayOfWeek) {
        return workingDays.isEmpty() || workingDays.contains(dayOfWeek);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Centre)) return false;
        Centre centre = (Centre) o;
        return Objects.equals(centreId, centre.centreId);
    }

    @Override public int hashCode() { return Objects.hash(centreId); }
}
