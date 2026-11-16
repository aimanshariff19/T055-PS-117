package com.ps117.billing;

import com.ps117.domain.RefundResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class RefundCalculator {
    public RefundResult calculate(BigDecimal feeAmount, LocalDateTime bookingTime, LocalDateTime cancellationTime) {
        RefundResult result = new RefundResult();
        long hours = Duration.between(bookingTime, cancellationTime).toHours();

        if (hours > 48) {
            result.setRefundAmount(feeAmount.subtract(new BigDecimal("100.00")));
            result.setReason("More than 48 hours");
        } else if (hours >= 24 && hours <= 48) {
            result.setRefundAmount(feeAmount.multiply(new BigDecimal("0.50")));
            result.setReason("Exactly 48 hours and up to 24 hours");
        } else {
            result.setRefundAmount(BigDecimal.ZERO);
            result.setReason("Less than 24 hours");
        }
        return result;
    }
}
