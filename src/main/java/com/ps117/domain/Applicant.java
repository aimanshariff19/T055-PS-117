package com.ps117.domain;

import java.time.LocalDate;
import java.util.Objects;

public class Applicant {
    private String applicantId;
    private String fullName;
    private LocalDate dateOfBirth;

    public Applicant() {}

    public Applicant(String applicantId, String fullName, LocalDate dateOfBirth) {
        this.applicantId = applicantId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
    }

    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public int ageOn(LocalDate asOfDate) {
        return dateOfBirth == null ? 0 : java.time.Period.between(dateOfBirth, asOfDate).getYears();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Applicant)) return false;
        Applicant applicant = (Applicant) o;
        return Objects.equals(applicantId, applicant.applicantId);
    }

    @Override public int hashCode() { return Objects.hash(applicantId); }
}
