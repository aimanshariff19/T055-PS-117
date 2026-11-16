package com.ps117.api;

final class ApiException extends RuntimeException {
    private final int statusCode;
    private final String code;

    ApiException(int statusCode, String code, String message) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    int statusCode() {
        return statusCode;
    }

    String code() {
        return code;
    }
}
