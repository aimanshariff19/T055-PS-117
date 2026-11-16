package com.ps117.lifecycle;

import com.ps117.domain.Application;
import com.ps117.enums.Category;

import java.util.ArrayList;
import java.util.List;

public class WalkInService {
    private final List<Application> walkIns = new ArrayList<>();
    private final int dailyLimit;

    public WalkInService(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean registerWalkIn(Application application) {
        if (application == null) return false;
        if (application.getCategory() == null) {
            return false;
        }
        if (application.getCategory() != Category.SENIOR_CITIZEN && application.getCategory() != Category.DIFFERENTLY_ABLED) {
            return false;
        }
        if (walkIns.size() >= dailyLimit) {
            return false;
        }
        walkIns.add(application);
        return true;
    }

    public List<Application> getWalkIns() {
        return walkIns;
    }
}
