package com.ps117.allocation;

import com.ps117.domain.QuotaAllocation;
import com.ps117.enums.QuotaType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuotaManager {
    private final Map<String, QuotaAllocation> quotas = new HashMap<>();

    public void addQuota(QuotaAllocation allocation) {
        quotas.put(key(allocation.getCentreId(), allocation.getAppointmentDate().toLocalDate(), allocation.getQuotaType()), allocation);
    }

    public int remaining(String centreId, java.time.LocalDate date, QuotaType type) {
        QuotaAllocation allocation = quotas.get(key(centreId, date, type));
        return allocation == null ? 0 : allocation.getRemainingCapacity();
    }

    public boolean consume(String centreId, java.time.LocalDate date, QuotaType type, int amount) {
        QuotaAllocation allocation = quotas.get(key(centreId, date, type));
        if (allocation == null || allocation.getRemainingCapacity() < amount) {
            return false;
        }
        allocation.consume(amount);
        return true;
    }

    public boolean release(String centreId, java.time.LocalDate date, QuotaType type) {
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
        allocation.release(Math.max(0, allocation.getRemainingCapacity()));
        return true;
    }

    public List<QuotaAllocation> getAll() {
        return new ArrayList<>(quotas.values());
    }

    private String key(String centreId, java.time.LocalDate date, QuotaType type) {
        return centreId + "|" + date + "|" + type.name();
    }
}
