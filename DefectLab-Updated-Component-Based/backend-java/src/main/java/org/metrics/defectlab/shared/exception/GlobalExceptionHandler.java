package org.metrics.defectlab.shared.exception;

import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort() {
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(org.metrics.defectlab.auth.security.CurrentUser.UnauthenticatedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticated(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(org.metrics.defectlab.auth.application.AuthService.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(org.metrics.defectlab.shared.exception.NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(org.metrics.defectlab.prediction.infrastructure.MlServiceClient.MlServiceException.class)
    public ResponseEntity<Map<String, String>> handleMlValidation(RuntimeException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded project archive exceeds the configured size limit.");
    }

    @ExceptionHandler(ExtractionBusyException.class)
    public ResponseEntity<Map<String, String>> handleExtractionBusy(ExtractionBusyException exception) {
        return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Map<String, String>> handleInputFailure(java.io.IOException exception) {
        LOGGER.warn("Metric extraction input failure", exception);
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception e) {
        LOGGER.error("Unhandled metric extraction failure", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "The server could not complete the request. Check the backend log for details.");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message == null || message.trim().isEmpty() ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(response);
    }
}
