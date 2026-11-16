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
        List<Application> ordered = new ArrayList<>(applications);
        ordered.sort(priorityComparator);
        for (Application application : ordered) {
            if (application == null) continue;
            if (application.getValidationStatus() == ValidationStatus.DOC_BLOCK || !application.isEligibleForAllocation()) {
                results.add(new AllocationResult(application.getApplicationId(), false, "DOC_BLOCK", null, null, application.getCategory() == null ? "" : application.getCategory().name()));
                continue;
            }
            if (application.getCategory() == null) {
                application.setCategory(calculateCategory(application, asOfDate));
            }
            Centre centre = centreMap.get(application.getCentrePreference());
            LocalDate appointmentDate = asOfDate.plusDays(1);
            if (centre == null) {
                results.add(new AllocationResult(application.getApplicationId(), false, "No centre preference", null, null, application.getCategory().name()));
                continue;
            }
            if (!bookingWindowValidator.isEligible(application, centre, appointmentDate, asOfDate)) {
                results.add(new AllocationResult(application.getApplicationId(), false, "Booking window invalid", null, null, application.getCategory().name()));
                continue;
            }

            Slot availableSlot = slots.stream()
                    .filter(s -> s.getCentreId().equals(centre.getCentreId()) && s.getDate().equals(appointmentDate) && s.getAvailableCapacity() > 0)
                    .sorted(Comparator.comparing(Slot::getStartTime))
                    .findFirst()
                    .orElse(null);

            if (availableSlot == null) {
                results.add(new AllocationResult(application.getApplicationId(), false, "No available slot", null, null, application.getCategory().name()));
                continue;
            }

            availableSlot.allocate();
            application.setAllocatedSlotId(availableSlot.getSlotId());
            application.setAllocatedCentreId(centre.getCentreId());
            application.setApplicationStatus(ApplicationStatus.ALLOCATED);
            results.add(new AllocationResult(application.getApplicationId(), true, "Allocated to slot", availableSlot.getSlotId(), centre.getCentreId(), application.getCategory().name()));
        }
        return results;
    }

    public Category calculateCategory(Application application, LocalDate asOfDate) {
        if (application == null) return Category.NORMAL;
        if (application.getCategoryFlags() != null && !application.getCategoryFlags().isEmpty()) {
            if (application.getCategoryFlags().contains(Category.TATKAL)) return Category.TATKAL;
            if (application.getCategoryFlags().contains(Category.SENIOR_CITIZEN)) return Category.SENIOR_CITIZEN;
            if (application.getCategoryFlags().contains(Category.MINOR)) return Category.MINOR;
            if (application.getCategoryFlags().contains(Category.DIFFERENTLY_ABLED)) return Category.DIFFERENTLY_ABLED;
        }
        if (application.isSeniorCitizenOn(asOfDate)) return Category.SENIOR_CITIZEN;
        if (application.isMinorOn(asOfDate)) return Category.MINOR;
        return Category.NORMAL;
    }
}
