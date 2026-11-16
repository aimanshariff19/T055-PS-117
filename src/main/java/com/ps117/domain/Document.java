package com.ps117.domain;

import com.ps117.enums.DocumentType;
import com.ps117.enums.VerificationStatus;

import java.time.LocalDate;
import java.util.Objects;

public class Document {
    private String documentId;
    private String applicationId;
    private DocumentType documentType;
    private VerificationStatus verificationStatus;
    private LocalDate expiryDate;
    private boolean required;
    private String reference;

    public Document() {}

    public Document(String documentId, String applicationId, DocumentType documentType,
                    VerificationStatus verificationStatus, LocalDate expiryDate, boolean required) {
        this.documentId = documentId;
        this.applicationId = applicationId;
        this.documentType = documentType;
        this.verificationStatus = verificationStatus;
        this.expiryDate = expiryDate;
        this.required = required;
    }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public boolean isExpiredOn(LocalDate comparisonDate) {
        return expiryDate != null && !expiryDate.isAfter(comparisonDate);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document)) return false;
        Document document = (Document) o;
        return Objects.equals(documentId, document.documentId);
    }

    @Override public int hashCode() { return Objects.hash(documentId); }
}
