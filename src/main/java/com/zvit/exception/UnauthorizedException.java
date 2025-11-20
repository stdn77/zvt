package com.zvit.exception;

/**
 * Exception для випадків недостатніх прав доступу
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
