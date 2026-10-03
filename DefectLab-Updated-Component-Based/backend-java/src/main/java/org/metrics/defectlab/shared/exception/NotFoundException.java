package org.metrics.defectlab.shared.exception;

/**
 * Thrown when a requested resource cannot be found or is not accessible.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
