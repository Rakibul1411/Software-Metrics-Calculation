package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

@Service
public class PredictionService {

    @Value("${fastapi.ml.service.url:http://localhost:8000/ml/predict}")
    private String mlServiceUrl;

    public Object runPrediction(Path targetCsvPath, MultipartFile[] sourceFiles, String labelColumnName, int knnValue, boolean coralOption) throws IOException {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // Add Target CSV File
        byte[] targetBytes = Files.readAllBytes(targetCsvPath);
        ByteArrayResource targetResource = new ByteArrayResource(targetBytes) {
            @Override
            public String getFilename() {
                return targetCsvPath.getFileName().toString();
            }
        };
        body.add("target_file", targetResource);

        // Add Source Labelled CSV Files
        for (MultipartFile file : sourceFiles) {
            ByteArrayResource sourceResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("source_files", sourceResource);
        }

        // Add options
        body.add("label_column", labelColumnName);
        body.add("knn_value", String.valueOf(knnValue));
        body.add("coral_option", String.valueOf(coralOption));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(mlServiceUrl, requestEntity, Object.class);
            return response.getBody();
        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", "Failed to communicate with Python FastAPI ML Service: " + e.getMessage());
            return errorMap;
        }
    }
}
