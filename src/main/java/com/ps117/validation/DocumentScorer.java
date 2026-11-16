package com.ps117.validation;

import com.ps117.domain.Document;
import com.ps117.enums.VerificationStatus;

import java.util.List;

public class DocumentScorer {
    public int score(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Document document : documents) {
            if (document == null || document.getVerificationStatus() == null) {
                continue;
            }
            switch (document.getVerificationStatus()) {
                case VERIFIED -> total += 2;
                case SELF_ATTESTED -> total += 1;
                case UNVERIFIED -> total += 0;
                default -> { }
            }
        }
        return total;
    }

    public int scoreForStatus(VerificationStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case VERIFIED -> 2;
            case SELF_ATTESTED -> 1;
            case UNVERIFIED -> 0;
        };
    }
}
