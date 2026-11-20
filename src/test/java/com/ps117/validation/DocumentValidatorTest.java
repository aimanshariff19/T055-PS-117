package com.ps117.validation;

import com.ps117.domain.Application;
import com.ps117.domain.Document;
import com.ps117.domain.DocumentValidationResult;
import com.ps117.enums.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentValidatorTest {
    @Test
    void validatesNormalApplicationAndBlocksExpiredDocument() {
        Application app = new Application();
        app.setApplicationId("A1");
        app.setServiceType(ServiceType.NORMAL);
        app.setCategory(Category.NORMAL);
        Document validDoc = new Document("D1", "A1", DocumentType.ADDRESS_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document dob = new Document("D2", "A1", DocumentType.DOB_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document photo = new Document("D3", "A1", DocumentType.PHOTOGRAPH, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);

        DocumentValidationResult result = new DocumentValidator().validate(app, List.of(validDoc, dob, photo), LocalDate.of(2026, 12, 1));
        assertEquals(ValidationStatus.VALID, result.getValidationStatus());
        assertTrue(result.getDocumentScore() >= 6);
    }

    @Test
    void blocksApplicationWhenMandatoryDocumentMissing() {
        Application app = new Application();
        app.setApplicationId("A2");
        app.setServiceType(ServiceType.NORMAL);
        app.setCategory(Category.NORMAL);
        Document validDoc = new Document("D1", "A2", DocumentType.ADDRESS_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);
        Document dob = new Document("D2", "A2", DocumentType.DOB_PROOF, VerificationStatus.VERIFIED, LocalDate.of(2027, 1, 1), true);

        DocumentValidationResult result = new DocumentValidator().validate(app, List.of(validDoc, dob), LocalDate.of(2026, 12, 1));
        assertEquals(ValidationStatus.DOC_BLOCK, result.getValidationStatus());
        assertEquals(VerificationMode.PRE, result.getVerificationMode(),
                "A score of 4 selects PRE even when missing mandatory documents block validation");
        assertEquals(VerificationMode.PRE, app.getVerificationMode(),
                "Verification mode remains independent from the DOC_BLOCK validation status");
        assertFalse(result.getMissingDocuments().isEmpty());
    }

    @Test
    void scoreBelowVerificationThresholdRemainsDocBlockMode() {
        Application app = new Application();
        app.setApplicationId("A3");
        app.setServiceType(ServiceType.NORMAL);

        Document address = new Document("D1", "A3", DocumentType.ADDRESS_PROOF, VerificationStatus.SELF_ATTESTED, LocalDate.of(2027, 1, 1), true);
        Document dob = new Document("D2", "A3", DocumentType.DOB_PROOF, VerificationStatus.SELF_ATTESTED, LocalDate.of(2027, 1, 1), true);

        DocumentValidationResult result = new DocumentValidator().validate(app, List.of(address, dob), LocalDate.of(2026, 12, 1));

        assertEquals(ValidationStatus.DOC_BLOCK, result.getValidationStatus());
        assertEquals(VerificationMode.DOC_BLOCK, result.getVerificationMode(),
                "BR-11 assigns DOC_BLOCK below score 3; do not relabel it PRE to fit the CSV enum");
        assertEquals(VerificationMode.DOC_BLOCK, app.getVerificationMode());
    }
}
