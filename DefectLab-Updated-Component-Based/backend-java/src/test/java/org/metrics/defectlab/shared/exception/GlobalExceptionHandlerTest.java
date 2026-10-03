package org.metrics.defectlab.shared.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void returnsConflictMessageWithoutInternalPersistenceDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleConflict(
                new ConflictException("Dataset already exists: AEEEM / EQ / 3.4 / PREDEFINED."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Dataset already exists: AEEEM / EQ / 3.4 / PREDEFINED.",
                response.getBody().get("error"));
    }

    @Test
    void genericFailuresDoNotExposeInternalExceptionMessages() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleAllExceptions(
                new RuntimeException("SQL constraint [secret_internal_name]"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(
                "The server could not complete the request. Check the backend log for details.",
                response.getBody().get("error"));
    }

    @Test
    void mlServiceUnreachableReturnsServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleMlValidation(
                new org.metrics.defectlab.prediction.usecase.port.MlServiceClient.MlServiceException(
                        "The ML service is not reachable at http://localhost:8000. Start it with: uvicorn app.main:app --port 8000"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(
                "Machine learning service is currently unreachable. Please ensure the ML backend service is running.",
                response.getBody().get("error"));
    }

    @Test
    void mlServiceValidationErrorReturnsUnprocessableEntity() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleMlValidation(
                new org.metrics.defectlab.prediction.usecase.port.MlServiceClient.MlServiceException(
                        "Invalid metric columns for prediction model."));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("Invalid metric columns for prediction model.", response.getBody().get("error"));
    }

    @Test
    void transactionFailureReturnsServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleTransactionFailure(
                new org.springframework.transaction.TransactionSystemException("Could not roll back JPA transaction"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(
                "Database service is temporarily unavailable. Please try again shortly.",
                response.getBody().get("error"));
    }

    @Test
    void dataIntegrityViolationReturnsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate key violation"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                "The operation could not be completed due to a data conflict or constraint violation.",
                response.getBody().get("error"));
    }
}
