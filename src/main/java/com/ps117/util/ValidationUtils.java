package com.ps117.util;

public final class ValidationUtils {
    private ValidationUtils() {}

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
