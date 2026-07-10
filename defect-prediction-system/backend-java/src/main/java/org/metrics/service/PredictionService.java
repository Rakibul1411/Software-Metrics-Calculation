package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PredictionService {

    @Value("${fastapi.ml.service.url:http://localhost:8000/ml/predict}")
    private String mlServiceUrl;

    @Value("${fastapi.ml.service.evaluate.url:http://localhost:8000/ml/evaluate}")
    private String mlEvaluationUrl;

    public Object runPrediction(Path targetCsvPath, MultipartFile[] sourceFiles, String labelColumnName,
                                int knnValue, boolean coralOption, int topK) throws IOException {
        return sendRequest(Files.readAllBytes(targetCsvPath), targetCsvPath.getFileName().toString(),
                sourceFiles, labelColumnName, knnValue, coralOption, topK, mlServiceUrl);
    }

    public Object evaluatePrediction(MultipartFile targetFile, MultipartFile[] sourceFiles,
                                     String labelColumnName, int knnValue,
                                     boolean coralOption, int topK) throws IOException {
        String targetFilename = targetFile.getOriginalFilename() == null
                ? "target.csv" : targetFile.getOriginalFilename();
        return sendRequest(targetFile.getBytes(), targetFilename, sourceFiles,
                labelColumnName, knnValue, coralOption, topK, mlEvaluationUrl);
    }

    private Object sendRequest(byte[] targetBytes, String targetFilename,
                               MultipartFile[] sourceFiles, String labelColumnName,
                               int knnValue, boolean coralOption, int topK, String serviceUrl) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource targetResource = new ByteArrayResource(targetBytes) {
            @Override
            public String getFilename() {
                return targetFilename;
            }
        };
        body.add("target_file", targetResource);

        for (MultipartFile file : sourceFiles) {
            ByteArrayResource sourceResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("source_files", sourceResource);
        }

        body.add("label_column", labelColumnName);
        body.add("knn_value", String.valueOf(knnValue));
        body.add("coral_option", String.valueOf(coralOption));
        body.add("top_k", String.valueOf(topK));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(serviceUrl, requestEntity, Object.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", extractServiceError(e.getResponseBodyAsString()));
            return errorMap;
        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", "Failed to communicate with Python FastAPI ML Service: " + e.getMessage());
            return errorMap;
        }
    }

    private String extractServiceError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "The Python prediction service rejected the request.";
        }

        String detailPrefix = "{\"detail\":\"";
        if (responseBody.startsWith(detailPrefix) && responseBody.endsWith("\"}")) {
            return responseBody.substring(detailPrefix.length(), responseBody.length() - 2)
                    .replace("\\\"", "\"");
        }
        return "Python prediction service error: " + responseBody;
    }
}
