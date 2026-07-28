package org.metrics.common.exception;

public final class ExtractionBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExtractionBusyException(String message) {
        super(message);
    }
}
