package org.metrics.defectlab.auth.usecase.exception;

/** Raised for authentication failures so the API can answer 401 rather than 400. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
