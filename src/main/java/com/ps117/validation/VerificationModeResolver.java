package com.ps117.validation;

import com.ps117.domain.Application;
import com.ps117.enums.ServiceType;
import com.ps117.enums.VerificationMode;

public class VerificationModeResolver {
    public VerificationMode resolve(int score, Application application) {
        if (score < 3) {
            return VerificationMode.DOC_BLOCK;
        }
        if (application != null && application.getServiceType() == ServiceType.RE_ISSUE && !application.isAddressChangeFlag()) {
            return VerificationMode.NOT_REQUIRED;
        }
        if (score >= 6) return VerificationMode.POST;
        return VerificationMode.PRE;
    }
}
