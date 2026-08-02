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
}
