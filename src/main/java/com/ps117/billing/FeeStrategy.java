package com.ps117.billing;

import com.ps117.domain.Application;
import java.math.BigDecimal;

public interface FeeStrategy {
    BigDecimal calculate(Application application);
}
