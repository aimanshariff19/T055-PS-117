package com.ps117.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {
    private static final int SCALE = 2;

    private MoneyUtils() {}

    public static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
