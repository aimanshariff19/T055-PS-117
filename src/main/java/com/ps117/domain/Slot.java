package com.ps117.domain;

import com.ps117.enums.SlotStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class Slot {
    private String slotId;
    private String centreId;
    private LocalDate date;
    private LocalTime startTime;
    private int physicalCapacity;
    private int scheduledAllocationCount;
    private int overbookingAllowance;
    private int availableCapacity;
    private SlotStatus status;

    public Slot() {
        this.status = SlotStatus.OPEN;
    }

    public Slot(String slotId, String centreId, LocalDate date, LocalTime startTime, int physicalCapacity) {
        this.slotId = slotId;
        this.centreId = centreId;
        this.date = date;
        this.startTime = startTime;
        this.physicalCapacity = physicalCapacity;
        this.availableCapacity = physicalCapacity;
        this.status = SlotStatus.OPEN;
        this.overbookingAllowance = Math.floorDiv(physicalCapacity * 10, 100);
        if (physicalCapacity <= 7) {
            this.overbookingAllowance = 0;
        }
    }

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getCentreId() { return centreId; }
    public void setCentreId(String centreId) { this.centreId = centreId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public int getPhysicalCapacity() { return physicalCapacity; }
    public void setPhysicalCapacity(int physicalCapacity) {
        this.physicalCapacity = physicalCapacity;
        this.overbookingAllowance = Math.floorDiv(physicalCapacity * 10, 100);
        if (physicalCapacity <= 7) {
            this.overbookingAllowance = 0;
        }
        this.availableCapacity = Math.max(0, physicalCapacity - scheduledAllocationCount);
    }
    public int getScheduledAllocationCount() { return scheduledAllocationCount; }
    public void setScheduledAllocationCount(int scheduledAllocationCount) {
        this.scheduledAllocationCount = scheduledAllocationCount;
        this.availableCapacity = Math.max(0, physicalCapacity - scheduledAllocationCount);
    }
    public int getOverbookingAllowance() { return overbookingAllowance; }
    public void setOverbookingAllowance(int overbookingAllowance) { this.overbookingAllowance = overbookingAllowance; }
    public int getAvailableCapacity() { return availableCapacity; }
    public void setAvailableCapacity(int availableCapacity) { this.availableCapacity = availableCapacity; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }

    public boolean canAllocate() {
        return scheduledAllocationCount < (physicalCapacity + overbookingAllowance);
    }

    public boolean isOverbooked() {
        return scheduledAllocationCount >= physicalCapacity;
    }

    public boolean isOverbookAllowed() {
        return overbookingAllowance > 0;
    }

    public boolean allocate() {
        if (!canAllocate()) {
            throw new IllegalStateException("No capacity available in slot: " + slotId);
        }
        boolean isOverflow = (scheduledAllocationCount >= physicalCapacity);
        scheduledAllocationCount++;
        availableCapacity = Math.max(0, physicalCapacity - scheduledAllocationCount);
        if (scheduledAllocationCount >= (physicalCapacity + overbookingAllowance)) {
            status = SlotStatus.FULL;
        }
        return isOverflow;
    }

    public void releaseOne() {
        if (scheduledAllocationCount > 0) {
            scheduledAllocationCount--;
            availableCapacity = Math.max(0, physicalCapacity - scheduledAllocationCount);
            if (scheduledAllocationCount < (physicalCapacity + overbookingAllowance)) {
                status = SlotStatus.OPEN;
            }
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Slot)) return false;
        Slot slot = (Slot) o;
        return Objects.equals(slotId, slot.slotId);
    }

    @Override public int hashCode() { return Objects.hash(slotId); }
}
