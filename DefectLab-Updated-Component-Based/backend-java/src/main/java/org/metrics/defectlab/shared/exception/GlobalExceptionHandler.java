package org.metrics.defectlab.shared.exception;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + (err.getDefaultMessage() != null ? err.getDefaultMessage() : "invalid"))
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed for request parameters.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "Malformed JSON request body.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, exception.getMessage());
    }

    @ExceptionHandler(org.metrics.defectlab.auth.security.CurrentUser.UnauthenticatedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticated(RuntimeException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(org.metrics.defectlab.auth.usecase.exception.InvalidCredentialsException.class)
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        LOGGER.warn("Database integrity or constraint violation", exception);
        return error(HttpStatus.CONFLICT,
                "The operation could not be completed due to a data conflict or constraint violation.");
    }

    @ExceptionHandler(org.metrics.defectlab.prediction.usecase.port.MlServiceClient.MlServiceException.class)
    public ResponseEntity<Map<String, String>> handleMlValidation(
            org.metrics.defectlab.prediction.usecase.port.MlServiceClient.MlServiceException exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : "";
        if (message.contains("not reachable") || message.contains("Connection refused")
                || message.contains("ConnectException") || message.contains("timed out")) {
            LOGGER.warn("ML service is unreachable: {}", message);
            return error(HttpStatus.SERVICE_UNAVAILABLE,
                    "Machine learning service is currently unreachable. Please ensure the ML backend service is running.");
        }
        return error(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    @ExceptionHandler({
            TransactionSystemException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<Map<String, String>> handleTransactionFailure(Exception exception) {
        LOGGER.error("Database connection or transaction failure", exception);
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "Database service is temporarily unavailable. Please try again shortly.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccessException(DataAccessException exception) {
        LOGGER.error("Database access error", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred while processing your request.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded project archive exceeds the configured size limit.");
    }

    @ExceptionHandler(ExtractionBusyException.class)
    public ResponseEntity<Map<String, String>> handleExtractionBusy(ExtractionBusyException exception) {
        return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
        return error(exception.getStatus(),
                exception.getReason() != null ? exception.getReason() : exception.getStatus().getReasonPhrase());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleInputFailure(IOException exception) {
        LOGGER.warn("I/O operation failure", exception);
        return error(HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage() != null && !exception.getMessage().isBlank()
                        ? exception.getMessage()
                        : "File processing failed.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception e) {
        LOGGER.error("Unhandled application failure", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "The server could not complete the request. Check the backend log for details.");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message == null || message.trim().isEmpty() ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(response);
    }
}
