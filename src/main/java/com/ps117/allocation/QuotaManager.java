package com.ps117.allocation;

import com.ps117.domain.Application;
import com.ps117.domain.Centre;
import com.ps117.domain.QuotaAllocation;
import com.ps117.enums.Category;
import com.ps117.enums.QuotaType;
import com.ps117.enums.ServiceType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuotaManager {
    private final Map<String, QuotaAllocation> quotas = new HashMap<>();

    public void initializeQuotas(Centre centre, LocalDate date) {
        int daily = centre.getDailyCapacity();
        int tatkal = Math.floorDiv(daily * 20, 100);
        int seniorMinor = Math.floorDiv(daily * 15, 100);
        int pcc = Math.floorDiv(daily * 10, 100);
        int normal = Math.max(0, daily - tatkal - seniorMinor - pcc);

        addQuota(new QuotaAllocation(centre.getCentreId(), date.atStartOfDay(), QuotaType.TATKAL, tatkal));
        addQuota(new QuotaAllocation(centre.getCentreId(), date.atStartOfDay(), QuotaType.SENIOR_MINOR, seniorMinor));
        addQuota(new QuotaAllocation(centre.getCentreId(), date.atStartOfDay(), QuotaType.PCC, pcc));
        addQuota(new QuotaAllocation(centre.getCentreId(), date.atStartOfDay(), QuotaType.NORMAL, normal));
        addQuota(new QuotaAllocation(centre.getCentreId(), date.atStartOfDay(), QuotaType.RELEASED, 0));
    }

    public void addQuota(QuotaAllocation allocation) {
        quotas.put(key(allocation.getCentreId(), allocation.getAppointmentDate().toLocalDate(), allocation.getQuotaType()), allocation);
    }

    public QuotaAllocation getQuota(String centreId, LocalDate date, QuotaType type) {
        return quotas.get(key(centreId, date, type));
    }

    public int remaining(String centreId, LocalDate date, QuotaType type) {
        QuotaAllocation allocation = quotas.get(key(centreId, date, type));
        return allocation == null ? 0 : allocation.getRemainingCapacity();
    }

    public boolean consume(String centreId, LocalDate date, QuotaType type, int amount) {
        QuotaAllocation allocation = quotas.get(key(centreId, date, type));
        if (allocation == null || allocation.getRemainingCapacity() < amount) {
            return false;
        }
        allocation.consume(amount);
        return true;
    }

    public boolean release(String centreId, LocalDate date, QuotaType type) {
        QuotaAllocation allocation = quotas.get(key(centreId, date, type));
        if (allocation == null) {
            return false;
        }
        if (allocation.getQuotaType() == QuotaType.TATKAL) {
            return false;
        }
        if (allocation.isReleased()) {
            return false;
        }
        int availableToRelease = Math.max(0, allocation.getRemainingCapacity());
        allocation.release(availableToRelease);

        QuotaAllocation releasedPool = quotas.get(key(centreId, date, QuotaType.RELEASED));
        if (releasedPool != null) {
            releasedPool.setOriginalQuota(releasedPool.getOriginalQuota() + availableToRelease);
            releasedPool.setRemainingCapacity(releasedPool.getRemainingCapacity() + availableToRelease);
            releasedPool.setUnusedQuota(releasedPool.getUnusedQuota() + availableToRelease);
        }
        return true;
    }

    public boolean releaseAllEligible(String centreId, LocalDate date) {
        boolean r1 = release(centreId, date, QuotaType.SENIOR_MINOR);
        boolean r2 = release(centreId, date, QuotaType.PCC);
        return r1 || r2;
    }

    public QuotaType consumeForApplication(Application application, Centre centre, LocalDate appointmentDate) {
        if (application == null || centre == null || appointmentDate == null) {
            return null;
        }
        String centreId = centre.getCentreId();

        if (getQuota(centreId, appointmentDate, QuotaType.NORMAL) == null) {
            initializeQuotas(centre, appointmentDate);
        }

        Category category = application.getCategory();
        ServiceType service = application.getServiceType();

        if (category == Category.TATKAL || service == ServiceType.TATKAL) {
            if (consume(centreId, appointmentDate, QuotaType.TATKAL, 1)) {
                return QuotaType.TATKAL;
            }
            return null;
        }

        if (category == Category.SENIOR_CITIZEN || category == Category.MINOR) {
            if (consume(centreId, appointmentDate, QuotaType.SENIOR_MINOR, 1)) {
                return QuotaType.SENIOR_MINOR;
            }
            if (consume(centreId, appointmentDate, QuotaType.RELEASED, 1)) {
                return QuotaType.RELEASED;
            }
            if (consume(centreId, appointmentDate, QuotaType.NORMAL, 1)) {
                return QuotaType.NORMAL;
            }
            return null;
        }

        if (service == ServiceType.PCC) {
            if (consume(centreId, appointmentDate, QuotaType.PCC, 1)) {
                return QuotaType.PCC;
            }
            if (consume(centreId, appointmentDate, QuotaType.RELEASED, 1)) {
                return QuotaType.RELEASED;
            }
            if (consume(centreId, appointmentDate, QuotaType.NORMAL, 1)) {
                return QuotaType.NORMAL;
            }
            return null;
        }

        if (consume(centreId, appointmentDate, QuotaType.RELEASED, 1)) {
            return QuotaType.RELEASED;
        }
        if (consume(centreId, appointmentDate, QuotaType.NORMAL, 1)) {
            return QuotaType.NORMAL;
        }
        return null;
    }

    public List<QuotaAllocation> getAll() {
        return new ArrayList<>(quotas.values());
    }

    /**
     * Return (un-consume) a quota unit back to its pool.
     * Used when a slot can't be found after quota was consumed (rollback).
     */
    public void returnQuota(String centreId, LocalDate date, QuotaType type) {
        QuotaAllocation allocation = quotas.get(key(centreId, date, type));
        if (allocation == null) return;
        allocation.setUsedQuota(Math.max(0, allocation.getUsedQuota() - 1));
        allocation.setRemainingCapacity(allocation.getRemainingCapacity() + 1);
        allocation.setUnusedQuota(allocation.getUnusedQuota() + 1);
    }

    /**
     * Consume a specific quota type (used for rollback scenarios).
     */
    public boolean consumeSpecific(String centreId, LocalDate date, QuotaType type) {
        return consume(centreId, date, type, 1);
    }

    private String key(String centreId, LocalDate date, QuotaType type) {
        return centreId + "|" + date + "|" + type.name();
    }
}
