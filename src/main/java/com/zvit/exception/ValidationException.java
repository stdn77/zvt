package com.zvit.exception;

/**
 * Exception для випадків валідації даних
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
