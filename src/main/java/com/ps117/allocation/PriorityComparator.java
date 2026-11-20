package com.ps117.allocation;

import com.ps117.domain.Application;
import com.ps117.enums.Category;

import java.util.Comparator;

public class PriorityComparator implements Comparator<Application> {
    @Override
    public int compare(Application a, Application b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        int categoryOrder = priorityRank(a.getCategory()) - priorityRank(b.getCategory());
        if (categoryOrder != 0) return categoryOrder;

        if (a.getApplicationTimestamp() != null && b.getApplicationTimestamp() != null) {
            int ts = a.getApplicationTimestamp().compareTo(b.getApplicationTimestamp());
            if (ts != 0) return ts;
        }

        if (a.getRescheduleCount() != b.getRescheduleCount()) {
            return Integer.compare(a.getRescheduleCount(), b.getRescheduleCount());
        }

        return String.CASE_INSENSITIVE_ORDER.compare(
                a.getApplicationId() == null ? "" : a.getApplicationId(),
                b.getApplicationId() == null ? "" : b.getApplicationId()
        );
    }

    public int priorityRank(Category category) {
        if (category == null) return 999;
        return switch (category) {
            case TATKAL -> 1;
            case SENIOR_CITIZEN -> 2;
            case MINOR -> 3;
            case DIFFERENTLY_ABLED -> 4;
            case NORMAL -> 5;
        };
    }
}
