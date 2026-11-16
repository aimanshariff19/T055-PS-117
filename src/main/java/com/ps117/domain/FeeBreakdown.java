package com.ps117.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeeBreakdown {
    private String applicationId;
    private BigDecimal baseFee = BigDecimal.ZERO;
    private BigDecimal tatkalSurcharge = BigDecimal.ZERO;
    private BigDecimal seniorDiscount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private Map<String, BigDecimal> lineItems = new LinkedHashMap<>();

    public FeeBreakdown() {}

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public BigDecimal getTatkalSurcharge() { return tatkalSurcharge; }
    public void setTatkalSurcharge(BigDecimal tatkalSurcharge) { this.tatkalSurcharge = tatkalSurcharge; }
    public BigDecimal getSeniorDiscount() { return seniorDiscount; }
    public void setSeniorDiscount(BigDecimal seniorDiscount) { this.seniorDiscount = seniorDiscount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public Map<String, BigDecimal> getLineItems() { return lineItems; }
    public void setLineItems(Map<String, BigDecimal> lineItems) { this.lineItems = lineItems; }

    public void addLineItem(String label, BigDecimal value) {
        lineItems.put(label, value);
    }
}
