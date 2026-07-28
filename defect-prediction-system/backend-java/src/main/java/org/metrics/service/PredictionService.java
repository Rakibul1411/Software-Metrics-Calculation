package org.metrics.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.metrics.common.dto.PredictionModelOptions;
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
                                boolean coralOption, int topK,
                                PredictionModelOptions modelOptions) throws IOException {
        return sendRequest(Files.readAllBytes(targetCsvPath), targetCsvPath.getFileName().toString(),
                sourceFiles, labelColumnName, coralOption, topK, modelOptions, mlServiceUrl);
    }

    public Object evaluatePrediction(MultipartFile targetFile, MultipartFile[] sourceFiles,
                                     String labelColumnName, boolean coralOption, int topK,
                                     PredictionModelOptions modelOptions) throws IOException {
        String targetFilename = targetFile.getOriginalFilename() == null
                ? "target.csv" : targetFile.getOriginalFilename();
        return sendRequest(targetFile.getBytes(), targetFilename, sourceFiles,
                labelColumnName, coralOption, topK, modelOptions, mlEvaluationUrl);
    }

    private Object sendRequest(byte[] targetBytes, String targetFilename,
                               MultipartFile[] sourceFiles, String labelColumnName,
                               boolean coralOption, int topK, PredictionModelOptions modelOptions,
                               String serviceUrl) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = buildRequestBody(
                targetBytes, targetFilename, sourceFiles, labelColumnName,
                coralOption, topK, modelOptions);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(serviceUrl, requestEntity, Object.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            return errorResponse(extractServiceError(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return errorResponse("Failed to communicate with Python FastAPI ML Service: " + e.getMessage());
        }
    }

    MultiValueMap<String, Object> buildRequestBody(
            byte[] targetBytes, String targetFilename,
            MultipartFile[] sourceFiles, String labelColumnName,
            boolean coralOption, int topK, PredictionModelOptions modelOptions) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("target_file", new ByteArrayResource(targetBytes) {
            @Override
            public String getFilename() {
                return targetFilename;
            }
        });

        for (MultipartFile file : sourceFiles) {
            body.add("source_files", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
        }

        body.add("label_column", labelColumnName);
        body.add("classifier", modelOptions.getClassifier().getApiValue());
        // Retained for compatibility with earlier ML-service versions.
        body.add("classifier_type", modelOptions.getClassifier().getApiValue());
        body.add("knn_value", String.valueOf(modelOptions.getKnnValue()));
        body.add("auto_tune_k", String.valueOf(modelOptions.isAutoTuneK()));
        body.add("svm_c", String.valueOf(modelOptions.getSvmC()));
        body.add("auto_tune_svm_c", String.valueOf(modelOptions.isAutoTuneSvmC()));
        body.add("threshold_beta", String.valueOf(modelOptions.getThresholdBeta()));
        if (modelOptions.getDecisionThreshold() != null) {
            body.add("decision_threshold", String.valueOf(modelOptions.getDecisionThreshold()));
        }
        body.add("coral_option", String.valueOf(coralOption));
        body.add("top_k", String.valueOf(topK));
        return body;
    }

    private Map<String, Object> errorResponse(String message) {
        return Map.of("status", "error", "message", message);
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
