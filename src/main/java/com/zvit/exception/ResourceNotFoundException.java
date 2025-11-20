package com.zvit.exception;

/**
 * Exception для випадків коли ресурс не знайдено
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
