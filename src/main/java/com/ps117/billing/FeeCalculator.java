package com.ps117.billing;

import com.ps117.domain.Application;
import com.ps117.domain.FeeBreakdown;
import com.ps117.enums.BookletSize;
import com.ps117.enums.Category;
import com.ps117.enums.PassportCondition;
import com.ps117.enums.ServiceType;
import com.ps117.util.MoneyUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

public class FeeCalculator {
    public static final BigDecimal BASE_NORMAL_36 = new BigDecimal("1500.00");
    public static final BigDecimal BASE_NORMAL_60 = new BigDecimal("2000.00");
    public static final BigDecimal TATKAL_SURCHARGE = new BigDecimal("2000.00");
    public static final BigDecimal MINOR_BASE = new BigDecimal("1000.00");
    public static final BigDecimal PCC_BASE = new BigDecimal("500.00");
    public static final BigDecimal REISSUE_BASE = new BigDecimal("3000.00");
    public static final BigDecimal REISSUE_60 = new BigDecimal("3500.00");
    public static final BigDecimal LOST_DAMAGED_36 = new BigDecimal("3000.00");
    public static final BigDecimal LOST_DAMAGED_60 = new BigDecimal("3500.00");
    public static final BigDecimal SENIOR_DISCOUNT_RATE = new BigDecimal("0.10");

    public BigDecimal calculate(Application application) {
        return calculate(application, null);
    }

    public BigDecimal calculate(Application application, LocalDate appointmentDate) {
        FeeBreakdown breakdown = breakdown(application, appointmentDate);
        return breakdown.getTotal();
    }

    public FeeBreakdown breakdown(Application application) {
        return breakdown(application, null);
    }

    public FeeBreakdown breakdown(Application application, LocalDate appointmentDate) {
        FeeBreakdown breakdown = new FeeBreakdown();
        if (application == null) {
            return breakdown;
        }
        breakdown.setApplicationId(application.getApplicationId());

        LocalDate effectiveDate = appointmentDate;
        if (effectiveDate == null && application.getApplicationTimestamp() != null) {
            effectiveDate = application.getApplicationTimestamp().toLocalDate();
        }

        boolean isSenior = (application.getCategory() == Category.SENIOR_CITIZEN)
                || (application.getCategoryFlags() != null && application.getCategoryFlags().contains(Category.SENIOR_CITIZEN))
                || (effectiveDate != null && application.isSeniorCitizenOn(effectiveDate));

        boolean isMinorUnder15 = (effectiveDate != null && application.isMinorOn(effectiveDate))
                || (application.getCategory() == Category.MINOR && (effectiveDate == null || application.isMinorOn(effectiveDate)));

        BigDecimal base = BigDecimal.ZERO;
        BigDecimal tatkal = BigDecimal.ZERO;

        ServiceType service = application.getServiceType();
        BookletSize booklet = application.getBookletSize() == null ? BookletSize.PAGES_36 : application.getBookletSize();

        if (service == ServiceType.PCC) {
            base = PCC_BASE;
        } else if (application.getPassportCondition() == PassportCondition.LOST || application.getPassportCondition() == PassportCondition.DAMAGED) {
            base = booklet == BookletSize.PAGES_60 ? LOST_DAMAGED_60 : LOST_DAMAGED_36;
        } else if (service == ServiceType.RE_ISSUE) {
            if (application.getOldPassportExpiry() != null && effectiveDate != null) {
                LocalDate expiry = application.getOldPassportExpiry();
                if (expiry.isBefore(effectiveDate) && Period.between(expiry, effectiveDate).getYears() >= 3) {
                    base = REISSUE_BASE; // 3000.00 for expired over 3 years
                } else {
                    // Within 1 year of expiry (or ambiguous 1-3 years): standard normal fee
                    base = booklet == BookletSize.PAGES_60 ? BASE_NORMAL_60 : BASE_NORMAL_36;
                }
            } else {
                base = REISSUE_BASE;
            }
        } else if (isMinorUnder15 && service != ServiceType.TATKAL) {
            base = MINOR_BASE;
        } else {
            base = booklet == BookletSize.PAGES_60 ? BASE_NORMAL_60 : BASE_NORMAL_36;
        }

        if (service == ServiceType.TATKAL || (application.getCategoryFlags() != null && application.getCategoryFlags().contains(Category.TATKAL))) {
            tatkal = TATKAL_SURCHARGE;
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (isSenior) {
            discount = MoneyUtils.scale(base.multiply(SENIOR_DISCOUNT_RATE));
        }

        BigDecimal netBase = base.subtract(discount);
        BigDecimal total = netBase.add(tatkal);

        breakdown.setBaseFee(MoneyUtils.scale(base));
        breakdown.setSeniorDiscount(MoneyUtils.scale(discount));
        breakdown.setTatkalSurcharge(MoneyUtils.scale(tatkal));
        breakdown.setTotal(MoneyUtils.scale(total));
        breakdown.addLineItem("base_fee", MoneyUtils.scale(base));
        if (tatkal.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.addLineItem("tatkal_surcharge", MoneyUtils.scale(tatkal));
        }
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.addLineItem("senior_discount", MoneyUtils.scale(discount));
        }
        return breakdown;
    }
}
