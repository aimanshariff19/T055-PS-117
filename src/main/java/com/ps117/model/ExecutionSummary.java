package com.ps117.model;

public class ExecutionSummary {
    private int totalInputRecords;
    private int validApplications;
    private int invalidApplications;
    private int docBlockApplications;
    private int successfulAllocations;
    private int unallocatedApplications;
    private int eventsProcessed;
    private int refundsCalculated;
    private int processingErrors;
    private long executionDurationMs;
    private String command;

    public int getTotalInputRecords() { return totalInputRecords; }
    public void setTotalInputRecords(int totalInputRecords) { this.totalInputRecords = totalInputRecords; }
    public int getValidApplications() { return validApplications; }
    public void setValidApplications(int validApplications) { this.validApplications = validApplications; }
    public int getInvalidApplications() { return invalidApplications; }
    public void setInvalidApplications(int invalidApplications) { this.invalidApplications = invalidApplications; }
    public int getDocBlockApplications() { return docBlockApplications; }
    public void setDocBlockApplications(int docBlockApplications) { this.docBlockApplications = docBlockApplications; }
    public int getSuccessfulAllocations() { return successfulAllocations; }
    public void setSuccessfulAllocations(int successfulAllocations) { this.successfulAllocations = successfulAllocations; }
    public int getUnallocatedApplications() { return unallocatedApplications; }
    public void setUnallocatedApplications(int unallocatedApplications) { this.unallocatedApplications = unallocatedApplications; }
    public int getEventsProcessed() { return eventsProcessed; }
    public void setEventsProcessed(int eventsProcessed) { this.eventsProcessed = eventsProcessed; }
    public int getRefundsCalculated() { return refundsCalculated; }
    public void setRefundsCalculated(int refundsCalculated) { this.refundsCalculated = refundsCalculated; }
    public int getProcessingErrors() { return processingErrors; }
    public void setProcessingErrors(int processingErrors) { this.processingErrors = processingErrors; }
    public long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(long executionDurationMs) { this.executionDurationMs = executionDurationMs; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
}
