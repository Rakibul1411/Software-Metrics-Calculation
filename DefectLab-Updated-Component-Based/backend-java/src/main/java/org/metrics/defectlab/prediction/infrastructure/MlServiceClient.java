package org.metrics.defectlab.prediction.infrastructure;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Talks to the internal FastAPI service.
 *
 * <p>The ML service is never exposed to the browser; a shared token identifies
 * this caller, and validation failures are surfaced with the ML service's own
 * message so the dashboard can show a real reason.</p>
 */
@Component
public class MlServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String token;

    public MlServiceClient(RestTemplateBuilder builder,
                           @Value("${ml.service.base-url:http://localhost:8000}") String baseUrl,
                           @Value("${ml.service.token:local-development-token}") String token) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofMinutes(10))
                .build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> predict(Map<String, Object> request) {
        return post("/ml/predict", request, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluate(Map<String, Object> request) {
        return post("/ml/evaluate", request, Map.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return restTemplate.postForObject(baseUrl + path, new HttpEntity<>(body, headers()),
                    responseType);
        } catch (HttpStatusCodeException exception) {
            throw new MlServiceException(extractDetail(exception));
        } catch (org.springframework.web.client.ResourceAccessException exception) {
            throw new MlServiceException(
                    "The ML service is not reachable at " + baseUrl
                    + ". Start it with: uvicorn app.main:app --port 8000");
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-DefectLab-Service-Token", token);
        return headers;
    }

    /** FastAPI reports validation problems as {@code {"detail": "..."}}. */
    private String extractDetail(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "The ML service rejected the request (" + exception.getRawStatusCode() + ").";
        }
        int marker = body.indexOf("\"detail\"");
        if (marker < 0) {
            return body.length() > 400 ? body.substring(0, 400) : body;
        }
        int start = body.indexOf('"', marker + 8);
        start = body.indexOf('"', start + 1);
        int end = body.lastIndexOf('"');
        return start > 0 && end > start ? body.substring(start + 1, end)
                .replace("\\\"", "\"") : body;
    }

    public static class MlServiceException extends RuntimeException {
        public MlServiceException(String message) {
            super(message);
        }
    }
}
