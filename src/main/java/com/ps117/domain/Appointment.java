package com.ps117.domain;

import com.ps117.enums.AppointmentStatus;
import com.ps117.enums.Category;
import com.ps117.enums.ServiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

public class Appointment {
    private String appointmentId;
    private String applicationId;
    private String applicantId;
    private String centreId;
    private String slotId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private ServiceType serviceType;
    private Category category;
    private AppointmentStatus status;
    private LocalDateTime bookedAt;
    private BigDecimal feeAmount;
    private String notes;

    public Appointment() {
        status = AppointmentStatus.BOOKED;
    }

    public Appointment(String appointmentId, String applicationId, String applicantId, String centreId,
                       String slotId, LocalDate appointmentDate, LocalTime appointmentTime,
                       ServiceType serviceType, Category category, BigDecimal feeAmount) {
        this.appointmentId = appointmentId;
        this.applicationId = applicationId;
        this.applicantId = applicantId;
        this.centreId = centreId;
        this.slotId = slotId;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.serviceType = serviceType;
        this.category = category;
        this.status = AppointmentStatus.BOOKED;
        this.feeAmount = feeAmount;
        this.bookedAt = LocalDateTime.now();
    }

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getCentreId() { return centreId; }
    public void setCentreId(String centreId) { this.centreId = centreId; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public void setBookedAt(LocalDateTime bookedAt) { this.bookedAt = bookedAt; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Appointment)) return false;
        Appointment that = (Appointment) o;
        return Objects.equals(appointmentId, that.appointmentId);
    }

    @Override public int hashCode() { return Objects.hash(appointmentId); }
}
