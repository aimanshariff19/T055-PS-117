package com.ps117.repositories;

import com.ps117.domain.Application;

import java.util.ArrayList;
import java.util.List;

public class ApplicationRepository {
    private final List<Application> applications = new ArrayList<>();

    public void add(Application application) {
        applications.add(application);
    }

    public List<Application> findAll() {
        return applications;
    }
}
