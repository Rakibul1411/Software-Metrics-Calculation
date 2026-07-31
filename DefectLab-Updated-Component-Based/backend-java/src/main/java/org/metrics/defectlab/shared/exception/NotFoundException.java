package org.metrics.defectlab.shared.exception;

/**
 * Raised when a user-scoped lookup finds nothing.
 *
 * <p>A row that belongs to another account is reported as missing rather than
 * forbidden, so responses never confirm that an id exists.</p>
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
