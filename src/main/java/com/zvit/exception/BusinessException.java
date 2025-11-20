package com.zvit.exception;

/**
 * Exception для бізнес-логіки (наприклад, досягнуто ліміт учасників)
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
