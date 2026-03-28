package com.cosmos.fraud.common.exception;

public class ValidationException extends FraudPlatformException {

    private static final String ERROR_CODE = "VALIDATION_FAILED";

    public ValidationException(String message) {
        super(ERROR_CODE, message);
    }

    public ValidationException(String field, String reason) {
        super(ERROR_CODE, "Validation failed for field '" + field + "': " + reason);
    }
}
