package com.ps117.validation;

import com.ps117.domain.Application;
import com.ps117.domain.Document;
import com.ps117.enums.Category;
import com.ps117.enums.DocumentType;
import com.ps117.enums.ServiceType;
import com.ps117.enums.VerificationStatus;

import java.util.ArrayList;
import java.util.List;

public class DocumentChecklist {

    public List<String> validateRequirements(Application application, List<Document> validDocs) {
        List<String> missing = new ArrayList<>();
        if (application == null) return missing;

        ServiceType service = application.getServiceType();

        boolean hasAddressProof = validDocs.stream()
                .anyMatch(d -> d.getDocumentType() != null && d.getDocumentType().isAddressProof());
        boolean hasDobProof = validDocs.stream()
                .anyMatch(d -> d.getDocumentType() != null && d.getDocumentType().isDobProof());
        boolean hasPhoto = validDocs.stream()
                .anyMatch(d -> d.getDocumentType() != null && d.getDocumentType().isPhoto());
        boolean hasOldPassport = validDocs.stream()
                .anyMatch(d -> d.getDocumentType() == DocumentType.OLD_PASSPORT);
        boolean hasValidPassport = validDocs.stream()
                .anyMatch(d -> d.getDocumentType() == DocumentType.VALID_PASSPORT);
        boolean hasParentConsent = validDocs.stream()
                .anyMatch(d -> d.getDocumentType() != null && d.getDocumentType().isParentConsent());

        if (service == ServiceType.NORMAL || service == ServiceType.TATKAL) {
            if (!hasAddressProof) missing.add("Missing mandatory document: ADDRESS_PROOF");
            if (!hasDobProof) missing.add("Missing mandatory document: DOB_PROOF");
            if (!hasPhoto) missing.add("Missing mandatory document: PHOTOGRAPH");

            if (service == ServiceType.TATKAL) {
                if (application.getAadhaarVerificationStatus() != VerificationStatus.VERIFIED) {
                    missing.add("TATKAL requires VERIFIED Aadhaar");
                }
            }
        } else if (service == ServiceType.RE_ISSUE) {
            if (!hasOldPassport) missing.add("Missing mandatory document: OLD_PASSPORT");
            if (application.isAddressChangeFlag() && !hasAddressProof) {
                missing.add("Missing mandatory document: ADDRESS_PROOF");
            }
        } else if (service == ServiceType.PCC) {
            if (!hasValidPassport) missing.add("Missing mandatory document: VALID_PASSPORT");
            if (!hasAddressProof) missing.add("Missing mandatory document: ADDRESS_PROOF");
        }

        boolean isMinor = application.getCategory() == Category.MINOR
                || (application.getCategoryFlags() != null && application.getCategoryFlags().contains(Category.MINOR));
        if (isMinor && (service == ServiceType.NORMAL || service == ServiceType.TATKAL)) {
            if (!hasParentConsent) {
                missing.add("Missing mandatory document: PARENT_CONSENT");
            }
        }

        return missing;
    }

    public List<DocumentType> getMandatoryDocuments(Application application) {
        if (application == null) return new ArrayList<>();
        List<DocumentType> mandatory = new ArrayList<>();
        ServiceType service = application.getServiceType();
        if (service == ServiceType.NORMAL || service == ServiceType.TATKAL) {
            mandatory.add(DocumentType.ADDRESS_PROOF);
            mandatory.add(DocumentType.DOB_PROOF);
            mandatory.add(DocumentType.PHOTOGRAPH);
        } else if (service == ServiceType.RE_ISSUE) {
            mandatory.add(DocumentType.OLD_PASSPORT);
            if (application.isAddressChangeFlag()) {
                mandatory.add(DocumentType.ADDRESS_PROOF);
            }
        } else if (service == ServiceType.PCC) {
            mandatory.add(DocumentType.VALID_PASSPORT);
            mandatory.add(DocumentType.ADDRESS_PROOF);
        }
        if (application.getCategory() == Category.MINOR && (service == ServiceType.NORMAL || service == ServiceType.TATKAL)) {
            mandatory.add(DocumentType.PARENT_PASSPORT);
        }
        return mandatory;
    }
}
