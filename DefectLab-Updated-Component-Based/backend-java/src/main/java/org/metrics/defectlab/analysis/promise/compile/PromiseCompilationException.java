package org.metrics.defectlab.analysis.promise.compile;

/**
 * Raised when a PROMISE release cannot be compiled to bytecode. Strict
 * extraction fails loudly rather than falling back to approximate source-only
 * metrics.
 */
public class PromiseCompilationException extends RuntimeException {

    public PromiseCompilationException(String message) {
        super(message);
    }

    public PromiseCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
