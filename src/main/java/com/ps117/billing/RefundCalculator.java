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
        if (cancellationTime == null || appointmentDateTime == null) {
            result.setRefundAmount(BigDecimal.ZERO.setScale(2));
            result.setReason("Invalid cancellation or appointment timestamp");
            return result;
        }

        BigDecimal refundableAmount = feeAmount;
        if (isTatkal) {
            refundableAmount = feeAmount.subtract(TATKAL_SURCHARGE).max(BigDecimal.ZERO);
        }

        Duration untilAppointment = Duration.between(cancellationTime, appointmentDateTime);
        Duration twentyFourHours = Duration.ofHours(24);
        Duration fortyEightHours = Duration.ofHours(48);

        // Compare exact durations: toHours() truncates partial hours and can
        // incorrectly treat 48h 30m as exactly 48h.
        if (untilAppointment.compareTo(fortyEightHours) > 0) {
            BigDecimal refund = refundableAmount.subtract(CANCEL_PROCESSING_FEE);
            result.setRefundAmount(MoneyUtils.scale(refund.max(BigDecimal.ZERO)));
            result.setReason("More than 48 hours");
        } else if (untilAppointment.compareTo(twentyFourHours) >= 0) {
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
