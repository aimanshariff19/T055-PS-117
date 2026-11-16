package com.ps117.domain;

import com.ps117.enums.QuotaType;

import java.time.LocalDateTime;
import java.util.Objects;

public class QuotaAllocation {
    private String centreId;
    private LocalDateTime appointmentDate;
    private QuotaType quotaType;
    private int originalQuota;
    private int usedQuota;
    private int unusedQuota;
    private int releasedQuota;
    private boolean released;
    private LocalDateTime releaseTimestamp;
    private int remainingCapacity;

    public QuotaAllocation() {}

    public QuotaAllocation(String centreId, LocalDateTime appointmentDate, QuotaType quotaType, int originalQuota) {
        this.centreId = centreId;
        this.appointmentDate = appointmentDate;
        this.quotaType = quotaType;
        this.originalQuota = originalQuota;
        this.usedQuota = 0;
        this.unusedQuota = originalQuota;
        this.releasedQuota = 0;
        this.remainingCapacity = originalQuota;
    }

    public String getCentreId() { return centreId; }
    public void setCentreId(String centreId) { this.centreId = centreId; }
    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }
    public QuotaType getQuotaType() { return quotaType; }
    public void setQuotaType(QuotaType quotaType) { this.quotaType = quotaType; }
    public int getOriginalQuota() { return originalQuota; }
    public void setOriginalQuota(int originalQuota) { this.originalQuota = originalQuota; }
    public int getUsedQuota() { return usedQuota; }
    public void setUsedQuota(int usedQuota) { this.usedQuota = usedQuota; }
    public int getUnusedQuota() { return unusedQuota; }
    public void setUnusedQuota(int unusedQuota) { this.unusedQuota = unusedQuota; }
    public int getReleasedQuota() { return releasedQuota; }
    public void setReleasedQuota(int releasedQuota) { this.releasedQuota = releasedQuota; }
    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }
    public LocalDateTime getReleaseTimestamp() { return releaseTimestamp; }
    public void setReleaseTimestamp(LocalDateTime releaseTimestamp) { this.releaseTimestamp = releaseTimestamp; }
    public int getRemainingCapacity() { return remainingCapacity; }
    public void setRemainingCapacity(int remainingCapacity) { this.remainingCapacity = remainingCapacity; }

    public void consume(int value) {
        usedQuota += value;
        unusedQuota = Math.max(0, originalQuota - usedQuota);
        remainingCapacity = originalQuota - usedQuota;
    }

    public void release(int value) {
        if (quotaType == QuotaType.TATKAL) {
            return;
        }
        if (released) {
            return;
        }
        releasedQuota += value;
        released = true;
        releaseTimestamp = LocalDateTime.now();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuotaAllocation)) return false;
        QuotaAllocation that = (QuotaAllocation) o;
        return Objects.equals(centreId, that.centreId) && Objects.equals(appointmentDate, that.appointmentDate) && quotaType == that.quotaType;
    }

    @Override public int hashCode() { return Objects.hash(centreId, appointmentDate, quotaType); }
}
