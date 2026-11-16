package com.ps117.domain;

public class AllocationResult {
    private String applicationId;
    private boolean allocated;
    private String reason;
    private String slotId;
    private String centreId;
    private String category;

    public AllocationResult() {}

    public AllocationResult(String applicationId, boolean allocated, String reason, String slotId, String centreId, String category) {
        this.applicationId = applicationId;
        this.allocated = allocated;
        this.reason = reason;
        this.slotId = slotId;
        this.centreId = centreId;
        this.category = category;
    }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public boolean isAllocated() { return allocated; }
    public void setAllocated(boolean allocated) { this.allocated = allocated; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getCentreId() { return centreId; }
    public void setCentreId(String centreId) { this.centreId = centreId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
