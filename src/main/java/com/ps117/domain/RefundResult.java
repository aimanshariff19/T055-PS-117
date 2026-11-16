package com.ps117.domain;

import java.math.BigDecimal;

public class RefundResult {
    private String applicationId;
    private BigDecimal refundAmount = BigDecimal.ZERO;
    private String reason;
    private boolean duplicateCancellation;

    public RefundResult() {}

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isDuplicateCancellation() { return duplicateCancellation; }
    public void setDuplicateCancellation(boolean duplicateCancellation) { this.duplicateCancellation = duplicateCancellation; }
}
