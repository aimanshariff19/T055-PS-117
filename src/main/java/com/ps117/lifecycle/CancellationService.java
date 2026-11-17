package com.ps117.lifecycle;

import com.ps117.billing.RefundCalculator;
import com.ps117.domain.Appointment;
import com.ps117.domain.RefundResult;
import com.ps117.enums.AppointmentStatus;
import com.ps117.enums.Category;
import com.ps117.enums.ServiceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public class CancellationService {
    private final Set<String> processedCancellationIds = new HashSet<>();
    private final RefundCalculator refundCalculator = new RefundCalculator();

    public RefundResult cancel(Appointment appointment, LocalDateTime cancellationTime, BigDecimal feeAmount) {
        if (appointment == null) return null;

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || !processedCancellationIds.add(appointment.getAppointmentId())) {
            RefundResult duplicate = new RefundResult();
            duplicate.setApplicationId(appointment.getApplicationId());
            duplicate.setDuplicateCancellation(true);
            duplicate.setReason("Duplicate cancellation");
            duplicate.setRefundAmount(BigDecimal.ZERO);
            return duplicate;
        }

        LocalTime time = appointment.getAppointmentTime() != null ? appointment.getAppointmentTime() : LocalTime.of(9, 0);
        LocalDateTime appointmentDateTime = appointment.getAppointmentDate() != null
                ? appointment.getAppointmentDate().atTime(time)
                : cancellationTime.plusDays(2);

        boolean isTatkal = appointment.getServiceType() == ServiceType.TATKAL || appointment.getCategory() == Category.TATKAL;

        RefundResult result = refundCalculator.calculate(feeAmount, cancellationTime, appointmentDateTime, isTatkal);
        result.setApplicationId(appointment.getApplicationId());

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return result;
    }
}
