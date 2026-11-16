package com.ps117.domain;

import com.ps117.enums.EventType;

import java.time.LocalDateTime;

public class AppointmentEvent {
    private String eventId;
    private String applicationId;
    private EventType eventType;
    private LocalDateTime eventTimestamp;
    private String targetSlotId;
    private String details;
    private boolean processed;
    private String processingStatus;
    private String errorMessage;

    public AppointmentEvent() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public LocalDateTime getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(LocalDateTime eventTimestamp) { this.eventTimestamp = eventTimestamp; }
    public String getTargetSlotId() { return targetSlotId; }
    public void setTargetSlotId(String targetSlotId) { this.targetSlotId = targetSlotId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
