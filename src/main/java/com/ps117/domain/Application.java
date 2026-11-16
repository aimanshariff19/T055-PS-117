package com.ps117.domain;

import com.ps117.enums.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Application {
    private String applicationId;
    private String applicantId;
    private String fullName;
    private LocalDate dateOfBirth;
    private LocalDateTime applicationTimestamp;
    private ServiceType serviceType;
    private String centrePreference;
    private Set<Category> categoryFlags = new HashSet<>();
    private VerificationStatus aadhaarVerificationStatus;
    private boolean addressChangeFlag;
    private PassportCondition passportCondition;
    private BookletSize bookletSize;
    private String passportNumber;
    private List<String> documentRefs = new ArrayList<>();
    private Category category;
    private ApplicationStatus applicationStatus;
    private ValidationStatus validationStatus;
    private VerificationMode verificationMode;
    private Integer documentScore;
    private String blockingReason;
    private boolean eligibleForAllocation = true;
    private int rescheduleCount;
    private boolean thirdRescheduleMovedToBack;
    private String allocatedSlotId;
    private String allocatedCentreId;
    private String appointmentId;

    public Application() {
        this.applicationStatus = ApplicationStatus.SUBMITTED;
        this.validationStatus = ValidationStatus.VALID;
    }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public LocalDateTime getApplicationTimestamp() { return applicationTimestamp; }
    public void setApplicationTimestamp(LocalDateTime applicationTimestamp) { this.applicationTimestamp = applicationTimestamp; }
    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }
    public String getCentrePreference() { return centrePreference; }
    public void setCentrePreference(String centrePreference) { this.centrePreference = centrePreference; }
    public Set<Category> getCategoryFlags() { return categoryFlags; }
    public void setCategoryFlags(Set<Category> categoryFlags) { this.categoryFlags = categoryFlags == null ? new HashSet<>() : categoryFlags; }
    public VerificationStatus getAadhaarVerificationStatus() { return aadhaarVerificationStatus; }
    public void setAadhaarVerificationStatus(VerificationStatus aadhaarVerificationStatus) { this.aadhaarVerificationStatus = aadhaarVerificationStatus; }
    public boolean isAddressChangeFlag() { return addressChangeFlag; }
    public void setAddressChangeFlag(boolean addressChangeFlag) { this.addressChangeFlag = addressChangeFlag; }
    public PassportCondition getPassportCondition() { return passportCondition; }
    public void setPassportCondition(PassportCondition passportCondition) { this.passportCondition = passportCondition; }
    public BookletSize getBookletSize() { return bookletSize; }
    public void setBookletSize(BookletSize bookletSize) { this.bookletSize = bookletSize; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
    public List<String> getDocumentRefs() { return documentRefs; }
    public void setDocumentRefs(List<String> documentRefs) { this.documentRefs = documentRefs == null ? new ArrayList<>() : documentRefs; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public ApplicationStatus getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(ApplicationStatus applicationStatus) { this.applicationStatus = applicationStatus; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public void setValidationStatus(ValidationStatus validationStatus) { this.validationStatus = validationStatus; }
    public VerificationMode getVerificationMode() { return verificationMode; }
    public void setVerificationMode(VerificationMode verificationMode) { this.verificationMode = verificationMode; }
    public Integer getDocumentScore() { return documentScore; }
    public void setDocumentScore(Integer documentScore) { this.documentScore = documentScore; }
    public String getBlockingReason() { return blockingReason; }
    public void setBlockingReason(String blockingReason) { this.blockingReason = blockingReason; }
    public boolean isEligibleForAllocation() { return eligibleForAllocation; }
    public void setEligibleForAllocation(boolean eligibleForAllocation) { this.eligibleForAllocation = eligibleForAllocation; }
    public int getRescheduleCount() { return rescheduleCount; }
    public void setRescheduleCount(int rescheduleCount) { this.rescheduleCount = rescheduleCount; }
    public boolean isThirdRescheduleMovedToBack() { return thirdRescheduleMovedToBack; }
    public void setThirdRescheduleMovedToBack(boolean thirdRescheduleMovedToBack) { this.thirdRescheduleMovedToBack = thirdRescheduleMovedToBack; }
    public String getAllocatedSlotId() { return allocatedSlotId; }
    public void setAllocatedSlotId(String allocatedSlotId) { this.allocatedSlotId = allocatedSlotId; }
    public String getAllocatedCentreId() { return allocatedCentreId; }
    public void setAllocatedCentreId(String allocatedCentreId) { this.allocatedCentreId = allocatedCentreId; }
    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public boolean isSeniorCitizenOn(LocalDate appointmentDate) {
        return dateOfBirth != null && java.time.Period.between(dateOfBirth, appointmentDate).getYears() >= 60;
    }

    public boolean isMinorOn(LocalDate appointmentDate) {
        return dateOfBirth != null && java.time.Period.between(dateOfBirth, appointmentDate).getYears() < 15;
    }

    public void addCategory(Category category) {
        if (category != null) {
            categoryFlags.add(category);
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Application)) return false;
        Application that = (Application) o;
        return Objects.equals(applicationId, that.applicationId);
    }

    @Override public int hashCode() { return Objects.hash(applicationId); }
}
