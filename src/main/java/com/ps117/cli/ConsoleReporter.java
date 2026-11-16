package com.ps117.cli;

import com.ps117.model.ExecutionSummary;

public class ConsoleReporter {
    public static void printSummary(ExecutionSummary summary) {
        System.out.println("Execution summary");
        System.out.println("Total input records: " + summary.getTotalInputRecords());
        System.out.println("Valid applications: " + summary.getValidApplications());
        System.out.println("Invalid applications: " + summary.getInvalidApplications());
        System.out.println("DOC_BLOCK applications: " + summary.getDocBlockApplications());
        System.out.println("Successful allocations: " + summary.getSuccessfulAllocations());
        System.out.println("Unallocated applications: " + summary.getUnallocatedApplications());
        System.out.println("Events processed: " + summary.getEventsProcessed());
        System.out.println("Refunds calculated: " + summary.getRefundsCalculated());
        System.out.println("Processing errors: " + summary.getProcessingErrors());
        System.out.println("Execution duration (ms): " + summary.getExecutionDurationMs());
    }
}
