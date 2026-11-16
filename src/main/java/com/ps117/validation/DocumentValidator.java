package com.ps117.validation;

import com.ps117.domain.Application;
import com.ps117.domain.Document;
import com.ps117.domain.DocumentValidationResult;
import com.ps117.enums.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DocumentValidator {
    private final DocumentChecklist checklist = new DocumentChecklist();
    private final DocumentScorer scorer = new DocumentScorer();
    private final VerificationModeResolver verificationModeResolver = new VerificationModeResolver();

    public DocumentValidationResult validate(Application application, List<Document> documents, LocalDate appointmentDate) {
        DocumentValidationResult result = new DocumentValidationResult();
        result.setApplicationId(application.getApplicationId());
        if (application == null || documents == null) {
            result.setValidationStatus(ValidationStatus.DOC_BLOCK);
            result.setVerificationMode(VerificationMode.DOC_BLOCK);
            result.setBlockingGaps(List.of("Invalid document set"));
            return result;
        }

        List<Document> validDocs = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> blockingGaps = new ArrayList<>();
        Set<DocumentType> seen = new HashSet<>();

        for (Document doc : documents) {
            if (doc == null) continue;
            String label = doc.getDocumentType() == null ? "UNKNOWN" : doc.getDocumentType().name();
            result.getDocumentList().add(label);
            if (doc.getExpiryDate() != null && !doc.getExpiryDate().isAfter(appointmentDate)) {
                expired.add(label);
                continue;
            }
            if (doc.getDocumentType() != null) {
                seen.add(doc.getDocumentType());
            }
            validDocs.add(doc);
        }

        List<DocumentType> required = checklist.getMandatoryDocuments(application);
        for (DocumentType requiredType : required) {
            boolean found = validDocs.stream().anyMatch(d -> d.getDocumentType() == requiredType);
            if (!found) {
                missing.add(requiredType.name());
                blockingGaps.add("Missing mandatory document: " + requiredType.name());
            }
        }

        result.setValidDocuments(validDocs.stream().map(d -> d.getDocumentType().name()).collect(Collectors.toList()));
        result.setExpiredDocuments(expired);
        result.setMissingDocuments(missing);
        result.setBlockingGaps(blockingGaps);

        int score = scorer.score(validDocs);
        result.setDocumentScore(score);
        VerificationMode mode = verificationModeResolver.resolve(score, application);
        result.setVerificationMode(mode);

        if (!blockingGaps.isEmpty() || score < 3) {
            result.setValidationStatus(ValidationStatus.DOC_BLOCK);
            application.setApplicationStatus(ApplicationStatus.DOC_BLOCK);
            application.setValidationStatus(ValidationStatus.DOC_BLOCK);
            application.setEligibleForAllocation(false);
            application.setBlockingReason(String.join("; ", blockingGaps));
        } else {
            result.setValidationStatus(ValidationStatus.VALID);
            application.setApplicationStatus(ApplicationStatus.VALIDATED);
            application.setValidationStatus(ValidationStatus.VALID);
            application.setEligibleForAllocation(true);
        }
        application.setDocumentScore(score);
        application.setVerificationMode(mode);
        return result;
    }
}
