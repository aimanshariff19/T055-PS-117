package com.ps117.allocation;

import com.ps117.domain.*;
import com.ps117.enums.*;

import java.time.LocalDate;
import java.util.*;

public class AllocationEngine {
    private final PriorityComparator priorityComparator = new PriorityComparator();
    private final BookingWindowValidator bookingWindowValidator = new BookingWindowValidator();

    public List<AllocationResult> allocate(List<Application> applications, List<Centre> centres, List<Slot> slots, QuotaManager quotaManager, LocalDate asOfDate) {
        List<AllocationResult> results = new ArrayList<>();
        Map<String, Centre> centreMap = new HashMap<>();
        for (Centre centre : centres) {
            centreMap.put(centre.getCentreId(), centre);
        }

        Set<String> activeApplicants = new HashSet<>();

        LocalDate targetAppointmentDate = asOfDate.plusDays(1);
        for (Centre centre : centres) {
            targetAppointmentDate = com.ps117.util.DateUtils.nextWorkingDay(asOfDate, bookingWindowValidator.getHolidays());
            quotaManager.initializeQuotas(centre, targetAppointmentDate);
        }

        List<Application> ordered = new ArrayList<>(applications);
        for (Application application : ordered) {
            if (application.getCategory() == null) {
                application.setCategory(calculateCategory(application, targetAppointmentDate));
            }
        }
        ordered.sort(priorityComparator);

        for (Application application : ordered) {
            if (application == null) continue;
            String catName = application.getCategory() == null ? "" : application.getCategory().name();

            if (application.getValidationStatus() == ValidationStatus.DOC_BLOCK || !application.isEligibleForAllocation()) {
                String reason = application.getBlockingReason() != null ? application.getBlockingReason() : "DOC_BLOCK";
                results.add(new AllocationResult(application.getApplicationId(), false, reason, null, null, catName));
                continue;
            }

            if (activeApplicants.contains(application.getApplicantId())) {
                results.add(new AllocationResult(application.getApplicationId(), false, "Duplicate active appointment", null, null, catName));
                continue;
            }

            Centre centre = centreMap.get(application.getCentrePreference());
            if (centre == null) {
                results.add(new AllocationResult(application.getApplicationId(), false, "No centre preference", null, null, catName));
                continue;
            }

            LocalDate appointmentDate = targetAppointmentDate;
            if (!bookingWindowValidator.isEligible(application, centre, appointmentDate, asOfDate)) {
                results.add(new AllocationResult(application.getApplicationId(), false, "Booking window invalid", null, null, catName));
                continue;
            }

            QuotaType consumedQuota = quotaManager.consumeForApplication(application, centre, appointmentDate);
            if (consumedQuota == null) {
                results.add(new AllocationResult(application.getApplicationId(), false, "Quota exhausted", null, null, catName));
                continue;
            }

            Slot availableSlot = slots.stream()
                    .filter(s -> s.getCentreId().equals(centre.getCentreId()) && s.getDate().equals(appointmentDate) && s.canAllocate())
                    .sorted(Comparator.comparing(Slot::getStartTime))
                    .findFirst()
                    .orElse(null);

            if (availableSlot == null) {
                results.add(new AllocationResult(application.getApplicationId(), false, "No available slot", null, null, catName));
                continue;
            }

            boolean isOverbooked = availableSlot.allocate();
            application.setAllocatedSlotId(availableSlot.getSlotId());
            application.setAllocatedCentreId(centre.getCentreId());
            application.setApplicationStatus(ApplicationStatus.ALLOCATED);
            activeApplicants.add(application.getApplicantId());

            results.add(new AllocationResult(application.getApplicationId(), true, "Allocated to slot", availableSlot.getSlotId(), centre.getCentreId(), catName));
        }
        return results;
    }

    public Category calculateCategory(Application application, LocalDate appointmentDate) {
        if (application == null) return Category.NORMAL;

        if (application.getServiceType() == ServiceType.TATKAL) {
            return Category.TATKAL;
        }

        if (application.getCategoryFlags() != null && application.getCategoryFlags().contains(Category.SENIOR_CITIZEN)) {
            return Category.SENIOR_CITIZEN;
        }
        if (appointmentDate != null && application.isSeniorCitizenOn(appointmentDate)) {
            return Category.SENIOR_CITIZEN;
        }

        if (appointmentDate != null) {
            if (application.isUnder18On(appointmentDate)
                    || (application.getDateOfBirth() == null
                    && application.getCategoryFlags() != null
                    && application.getCategoryFlags().contains(Category.MINOR))) {
                return Category.MINOR;
            }
        } else if (application.getCategoryFlags() != null && application.getCategoryFlags().contains(Category.MINOR)) {
            return Category.MINOR;
        }

        if (application.getCategoryFlags() != null && application.getCategoryFlags().contains(Category.DIFFERENTLY_ABLED)) {
            return Category.DIFFERENTLY_ABLED;
        }

        return Category.NORMAL;
    }
}
