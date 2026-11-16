package com.ps117.validation;

import com.ps117.domain.Application;
import com.ps117.enums.DocumentType;
import com.ps117.enums.ServiceType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DocumentChecklist {
    public List<DocumentType> getMandatoryDocuments(Application application) {
        if (application == null) return new ArrayList<>();
        List<DocumentType> mandatory = new ArrayList<>();
        switch (application.getServiceType()) {
            case NORMAL -> {
                mandatory.addAll(List.of(DocumentType.ADDRESS_PROOF, DocumentType.DOB_PROOF, DocumentType.PHOTOGRAPH));
            }
            case RE_ISSUE -> {
                mandatory.add(DocumentType.OLD_PASSPORT);
                if (application.isAddressChangeFlag()) {
                    mandatory.add(DocumentType.ADDRESS_PROOF);
                }
            }
            case PCC -> {
                mandatory.add(DocumentType.VALID_PASSPORT);
                mandatory.add(DocumentType.ADDRESS_PROOF);
            }
            case TATKAL -> {
                mandatory.addAll(List.of(DocumentType.ADDRESS_PROOF, DocumentType.DOB_PROOF, DocumentType.PHOTOGRAPH));
            }
            default -> { }
        }
        if (application.getCategory() == com.ps117.enums.Category.MINOR) {
            mandatory.add(DocumentType.PARENT_PASSPORT);
            mandatory.add(DocumentType.ANNEXURE_D);
        }
        return mandatory;
    }

    public Set<DocumentType> getSupportedAddressProofs() {
        return EnumSet.of(
                DocumentType.ADDRESS_PROOF,
                DocumentType.VALID_PASSPORT,
                DocumentType.PARENT_PASSPORT
        );
    }

    public Set<DocumentType> getSupportedDobProofs() {
        return EnumSet.of(DocumentType.DOB_PROOF, DocumentType.VALID_PASSPORT, DocumentType.OLD_PASSPORT);
    }
}
