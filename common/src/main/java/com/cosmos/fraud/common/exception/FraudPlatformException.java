package com.cosmos.fraud.common.exception;

public class FraudPlatformException extends RuntimeException {

    private final String errorCode;

    public FraudPlatformException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FraudPlatformException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
