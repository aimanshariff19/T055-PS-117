package com.ps117.lifecycle;

import com.ps117.domain.Appointment;
import com.ps117.domain.RefundResult;
import com.ps117.enums.AppointmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class CancellationService {
    private final Set<String> processedCancellationIds = new HashSet<>();

    public RefundResult cancel(Appointment appointment, LocalDateTime cancellationTime, BigDecimal feeAmount) {
        if (appointment == null) return null;
        if (!processedCancellationIds.add(appointment.getAppointmentId())) {
            RefundResult duplicate = new RefundResult();
            duplicate.setApplicationId(appointment.getApplicationId());
            duplicate.setDuplicateCancellation(true);
            duplicate.setReason("Duplicate cancellation");
            return duplicate;
        }

        RefundResult result = new RefundResult();
        result.setApplicationId(appointment.getApplicationId());

        long hours = java.time.Duration.between(appointment.getBookedAt(), cancellationTime).toHours();
        if (hours > 48) {
            result.setRefundAmount(feeAmount.subtract(new java.math.BigDecimal("100.00")));
            result.setReason("More than 48 hours");
        } else if (hours >= 24 && hours <= 48) {
            result.setRefundAmount(feeAmount.multiply(new java.math.BigDecimal("0.50")));
            result.setReason("Exactly 48 hours to 24 hours");
        } else {
            result.setRefundAmount(BigDecimal.ZERO);
            result.setReason("Less than 24 hours");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return result;
    }
}
