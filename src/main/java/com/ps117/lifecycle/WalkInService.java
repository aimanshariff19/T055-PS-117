package com.ps117.lifecycle;

import com.ps117.domain.Application;
import com.ps117.enums.Category;

import java.util.ArrayList;
import java.util.List;

public class WalkInService {
    private final List<Application> walkIns = new ArrayList<>();
    private final int dailyLimit;

    public WalkInService(int dailyCapacity) {
        // EC-08: Walk-in cap of 10% rounded down. For 35 -> 3.
        this.dailyLimit = Math.floorDiv(dailyCapacity * 10, 100);
    }

    public static int computeCap(int dailyCapacity) {
        return Math.floorDiv(dailyCapacity * 10, 100);
    }

    public boolean registerWalkIn(Application application) {
        if (application == null) return false;
        Category category = application.getCategory();
        if (category == null && application.getCategoryFlags() != null && !application.getCategoryFlags().isEmpty()) {
            if (application.getCategoryFlags().contains(Category.SENIOR_CITIZEN)) category = Category.SENIOR_CITIZEN;
            else if (application.getCategoryFlags().contains(Category.DIFFERENTLY_ABLED)) category = Category.DIFFERENTLY_ABLED;
        }

        if (category != Category.SENIOR_CITIZEN && category != Category.DIFFERENTLY_ABLED) {
            return false;
        }
        if (walkIns.size() >= dailyLimit) {
            return false;
        }
        walkIns.add(application);
        return true;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public List<Application> getWalkIns() {
        return walkIns;
    }
}
