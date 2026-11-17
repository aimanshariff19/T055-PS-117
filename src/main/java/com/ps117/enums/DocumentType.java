package com.ps117.enums;

public enum DocumentType {
    ADDRESS_PROOF,
    DOB_PROOF,
    PHOTOGRAPH,
    PHOTO,
    OLD_PASSPORT,
    PARENT_PASSPORT,
    ANNEXURE_D,
    VALID_PASSPORT,
    AADHAAR,
    ELECTRICITY_BILL,
    WATER_BILL,
    BANK_PASSBOOK,
    RENT_AGREEMENT,
    GAS_CONNECTION,
    VOTER_ID,
    TELEPHONE_BILL,
    BIRTH_CERTIFICATE,
    SSLC_CERTIFICATE,
    PAN_CARD,
    DRIVING_LICENCE,
    POLICY_BOND;

    public boolean isAddressProof() {
        return switch (this) {
            case ADDRESS_PROOF, AADHAAR, ELECTRICITY_BILL, WATER_BILL, BANK_PASSBOOK,
                 RENT_AGREEMENT, GAS_CONNECTION, VOTER_ID, TELEPHONE_BILL -> true;
            default -> false;
        };
    }

    public boolean isDobProof() {
        return switch (this) {
            case DOB_PROOF, BIRTH_CERTIFICATE, SSLC_CERTIFICATE, PAN_CARD, DRIVING_LICENCE, POLICY_BOND -> true;
            default -> false;
        };
    }

    public boolean isPhoto() {
        return this == PHOTOGRAPH || this == PHOTO;
    }

    public boolean isParentConsent() {
        return this == PARENT_PASSPORT || this == ANNEXURE_D;
    }
}
