package com.ps117.domain;

import com.ps117.enums.ValidationStatus;
import com.ps117.enums.VerificationMode;

import java.util.ArrayList;
import java.util.List;

public class DocumentValidationResult {
    private String applicationId;
    private List<String> documentList = new ArrayList<>();
    private List<String> validDocuments = new ArrayList<>();
    private List<String> expiredDocuments = new ArrayList<>();
    private List<String> missingDocuments = new ArrayList<>();
    private List<String> blockingGaps = new ArrayList<>();
    private int documentScore;
    private VerificationMode verificationMode;
    private ValidationStatus validationStatus;

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public List<String> getDocumentList() { return documentList; }
    public void setDocumentList(List<String> documentList) { this.documentList = documentList; }
    public List<String> getValidDocuments() { return validDocuments; }
    public void setValidDocuments(List<String> validDocuments) { this.validDocuments = validDocuments; }
    public List<String> getExpiredDocuments() { return expiredDocuments; }
    public void setExpiredDocuments(List<String> expiredDocuments) { this.expiredDocuments = expiredDocuments; }
    public List<String> getMissingDocuments() { return missingDocuments; }
    public void setMissingDocuments(List<String> missingDocuments) { this.missingDocuments = missingDocuments; }
    public List<String> getBlockingGaps() { return blockingGaps; }
    public void setBlockingGaps(List<String> blockingGaps) { this.blockingGaps = blockingGaps; }
    public int getDocumentScore() { return documentScore; }
    public void setDocumentScore(int documentScore) { this.documentScore = documentScore; }
    public VerificationMode getVerificationMode() { return verificationMode; }
    public void setVerificationMode(VerificationMode verificationMode) { this.verificationMode = verificationMode; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public void setValidationStatus(ValidationStatus validationStatus) { this.validationStatus = validationStatus; }
}
