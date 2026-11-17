package com.ps117.billing;

import com.ps117.domain.RefundResult;
import com.ps117.util.MoneyUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class RefundCalculator {
    public static final BigDecimal CANCEL_PROCESSING_FEE = new BigDecimal("100.00");
    public static final BigDecimal TATKAL_SURCHARGE = new BigDecimal("2000.00");

    public RefundResult calculate(BigDecimal feeAmount, LocalDateTime cancellationTime, LocalDateTime appointmentDateTime, boolean isTatkal) {
        RefundResult result = new RefundResult();
        if (feeAmount == null) feeAmount = BigDecimal.ZERO;

        BigDecimal refundableAmount = feeAmount;
        if (isTatkal) {
            refundableAmount = feeAmount.subtract(TATKAL_SURCHARGE);
            if (refundableAmount.compareTo(BigDecimal.ZERO) < 0) {
                refundableAmount = BigDecimal.ZERO;
            }
        }

        long hours = Duration.between(cancellationTime, appointmentDateTime).toHours();

        if (hours > 48) {
            BigDecimal refund = refundableAmount.subtract(CANCEL_PROCESSING_FEE);
            result.setRefundAmount(MoneyUtils.scale(refund.max(BigDecimal.ZERO)));
            result.setReason("More than 48 hours");
        } else if (hours >= 24 && hours <= 48) {
            BigDecimal refund = refundableAmount.multiply(new BigDecimal("0.50"));
            result.setRefundAmount(MoneyUtils.scale(refund));
            result.setReason("Between 24 and 48 hours (inclusive)");
        } else {
            result.setRefundAmount(MoneyUtils.scale(BigDecimal.ZERO));
            result.setReason("Less than 24 hours (forfeited)");
        }
        return result;
    }

    public RefundResult calculate(BigDecimal feeAmount, LocalDateTime bookingTime, LocalDateTime cancellationTime) {
        return calculate(feeAmount, bookingTime, cancellationTime, false);
    }
}
