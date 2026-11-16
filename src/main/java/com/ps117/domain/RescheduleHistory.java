package com.ps117.domain;

import java.time.LocalDateTime;

public class RescheduleHistory {
    private String appointmentId;
    private String previousSlotId;
    private String newSlotId;
    private LocalDateTime rescheduleTimestamp;
    private String reason;

    public RescheduleHistory() {}

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }
    public String getPreviousSlotId() { return previousSlotId; }
    public void setPreviousSlotId(String previousSlotId) { this.previousSlotId = previousSlotId; }
    public String getNewSlotId() { return newSlotId; }
    public void setNewSlotId(String newSlotId) { this.newSlotId = newSlotId; }
    public LocalDateTime getRescheduleTimestamp() { return rescheduleTimestamp; }
    public void setRescheduleTimestamp(LocalDateTime rescheduleTimestamp) { this.rescheduleTimestamp = rescheduleTimestamp; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
