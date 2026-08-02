package org.metrics.defectlab.shared.exception;

/**
 * Raised when a valid request conflicts with an existing persisted resource.
 */
public final class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
