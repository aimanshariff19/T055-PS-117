package com.ps117.billing;

import com.ps117.domain.Application;
import com.ps117.domain.FeeBreakdown;
import com.ps117.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FeeCalculator implements FeeStrategy {
    public static final BigDecimal BASE_NORMAL_36 = new BigDecimal("1500.00");
    public static final BigDecimal BASE_NORMAL_60 = new BigDecimal("2000.00");
    public static final BigDecimal TATKAL_SURCHARGE = new BigDecimal("2000.00");
    public static final BigDecimal MINOR_BASE = new BigDecimal("1000.00");
    public static final BigDecimal PCC_BASE = new BigDecimal("500.00");
    public static final BigDecimal REISSUE_BASE = new BigDecimal("3000.00");
    public static final BigDecimal REISSUE_60 = new BigDecimal("3500.00");

    @Override
    public BigDecimal calculate(Application application) {
        if (application == null) return BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal surplus = BigDecimal.ZERO;
        if (application.getServiceType() == ServiceType.NORMAL) {
            base = application.getBookletSize() == BookletSize.PAGES_60 ? BASE_NORMAL_60 : BASE_NORMAL_36;
        } else if (application.getServiceType() == ServiceType.TATKAL) {
            base = application.getBookletSize() == BookletSize.PAGES_60 ? BASE_NORMAL_60 : BASE_NORMAL_36;
            surplus = TATKAL_SURCHARGE;
        } else if (application.getCategory() == Category.MINOR && application.getDateOfBirth() != null) {
            base = MINOR_BASE;
        } else if (application.getServiceType() == ServiceType.PCC) {
            base = PCC_BASE;
        } else if (application.getServiceType() == ServiceType.RE_ISSUE) {
            base = REISSUE_BASE;
            if (application.getPassportCondition() == PassportCondition.LOST || application.getPassportCondition() == PassportCondition.DAMAGED) {
                base = application.getBookletSize() == BookletSize.PAGES_60 ? REISSUE_60 : REISSUE_BASE;
            }
        }

        if (application.getCategory() == Category.SENIOR_CITIZEN) {
            BigDecimal discount = base.multiply(new BigDecimal("0.10"));
            base = base.subtract(discount);
        }
        BigDecimal total = base.add(surplus);
        return total.setScale(2);
    }

    public FeeBreakdown breakdown(Application application) {
        FeeBreakdown breakdown = new FeeBreakdown();
        breakdown.setApplicationId(application.getApplicationId());
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal tatkal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;

        if (application.getServiceType() == ServiceType.NORMAL) {
            base = application.getBookletSize() == BookletSize.PAGES_60 ? BASE_NORMAL_60 : BASE_NORMAL_36;
        } else if (application.getServiceType() == ServiceType.TATKAL) {
            base = application.getBookletSize() == BookletSize.PAGES_60 ? BASE_NORMAL_60 : BASE_NORMAL_36;
            tatkal = TATKAL_SURCHARGE;
        } else if (application.getCategory() == Category.MINOR && application.getDateOfBirth() != null) {
            base = MINOR_BASE;
        } else if (application.getServiceType() == ServiceType.PCC) {
            base = PCC_BASE;
        } else if (application.getServiceType() == ServiceType.RE_ISSUE) {
            base = REISSUE_BASE;
            if (application.getPassportCondition() == PassportCondition.LOST || application.getPassportCondition() == PassportCondition.DAMAGED) {
                base = application.getBookletSize() == BookletSize.PAGES_60 ? REISSUE_60 : REISSUE_BASE;
            }
        }

        if (application.getCategory() == Category.SENIOR_CITIZEN) {
            discount = base.multiply(new BigDecimal("0.10"));
            base = base.subtract(discount);
        }

        BigDecimal total = base.add(tatkal);
        breakdown.setBaseFee(base);
        breakdown.setTatkalSurcharge(tatkal);
        breakdown.setSeniorDiscount(discount);
        breakdown.setTotal(total);
        breakdown.addLineItem("base_fee", base);
        if (tatkal.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.addLineItem("tatkal_surcharge", tatkal);
        }
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.addLineItem("senior_discount", discount);
        }
        return breakdown;
    }
}
