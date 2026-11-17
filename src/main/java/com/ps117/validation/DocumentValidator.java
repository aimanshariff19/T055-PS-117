package com.ps117.validation;

import com.ps117.domain.Application;
import com.ps117.domain.Document;
import com.ps117.domain.DocumentValidationResult;
import com.ps117.enums.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentValidator {
    private final DocumentChecklist checklist = new DocumentChecklist();
    private final DocumentScorer scorer = new DocumentScorer();
    private final VerificationModeResolver verificationModeResolver = new VerificationModeResolver();

    public DocumentValidationResult validate(Application application, List<Document> documents, LocalDate appointmentDate) {
        DocumentValidationResult result = new DocumentValidationResult();
        if (application == null) {
            result.setValidationStatus(ValidationStatus.DOC_BLOCK);
            result.setVerificationMode(VerificationMode.DOC_BLOCK);
            result.setBlockingGaps(List.of("Missing application"));
            return result;
        }

        result.setApplicationId(application.getApplicationId());

        if (documents == null || documents.isEmpty()) {
            result.setValidationStatus(ValidationStatus.DOC_BLOCK);
            result.setVerificationMode(VerificationMode.DOC_BLOCK);
            result.setBlockingGaps(List.of("No documents provided"));
            application.setValidationStatus(ValidationStatus.DOC_BLOCK);
            application.setApplicationStatus(ApplicationStatus.DOC_BLOCK);
            application.setVerificationMode(VerificationMode.DOC_BLOCK);
            application.setDocumentScore(0);
            application.setEligibleForAllocation(false);
            application.setBlockingReason("No documents provided");
            return result;
        }

        List<Document> validDocs = new ArrayList<>();
        List<String> expired = new ArrayList<>();

        for (Document doc : documents) {
            if (doc == null) continue;
            String label = doc.getDocumentType() == null ? "UNKNOWN" : doc.getDocumentType().name();
            result.getDocumentList().add(label);

            // EC-04: A document expiring on the appointment date is not yet 'past', so it is still valid.
            // It is only expired if its valid_until date is strictly before the appointment date.
            if (doc.getExpiryDate() != null && doc.getExpiryDate().isBefore(appointmentDate)) {
                expired.add(label);
                continue;
            }
            validDocs.add(doc);
        }

        List<String> missingRequirements = checklist.validateRequirements(application, validDocs);
        List<String> blockingGaps = new ArrayList<>(missingRequirements);

        result.setValidDocuments(validDocs.stream().map(d -> d.getDocumentType() == null ? "UNKNOWN" : d.getDocumentType().name()).collect(Collectors.toList()));
        result.setExpiredDocuments(expired);
        result.setMissingDocuments(missingRequirements);

        int score = scorer.score(validDocs);
        result.setDocumentScore(score);

        if (score < 3) {
            blockingGaps.add("Document score " + score + " is below minimum threshold 3");
        }

        result.setBlockingGaps(blockingGaps);

        VerificationMode mode = verificationModeResolver.resolve(score, application);
        result.setVerificationMode(mode);

        if (!blockingGaps.isEmpty() || score < 3) {
            result.setValidationStatus(ValidationStatus.DOC_BLOCK);
            application.setApplicationStatus(ApplicationStatus.DOC_BLOCK);
            application.setValidationStatus(ValidationStatus.DOC_BLOCK);
            application.setEligibleForAllocation(false);
            application.setVerificationMode(VerificationMode.DOC_BLOCK);
            application.setBlockingReason(String.join("; ", blockingGaps));
        } else {
            result.setValidationStatus(ValidationStatus.VALID);
            application.setApplicationStatus(ApplicationStatus.VALIDATED);
            application.setValidationStatus(ValidationStatus.VALID);
            application.setEligibleForAllocation(true);
            application.setVerificationMode(mode);
            application.setBlockingReason(null);
        }
        application.setDocumentScore(score);
        return result;
    }
}
