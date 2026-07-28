package org.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.metrics.common.dto.PredictionModelOptions;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MultiValueMap;

class PredictionServiceTest {

    @Test
    void forwardsSvmOptionsUsingFastApiFieldNames() throws Exception {
        PredictionModelOptions options = PredictionModelOptions.create(
                "svm", 5, false, 0.25, true, null, 2.0);
        MockMultipartFile source = new MockMultipartFile(
                "sourceFiles", "source.csv", "text/csv", "name,bug\nA,1".getBytes());

        MultiValueMap<String, Object> body = new PredictionService().buildRequestBody(
                "name\nTarget".getBytes(), "target.csv",
                new MockMultipartFile[] { source }, "bug", true, 1, options);

        assertEquals("svm", body.getFirst("classifier_type"));
        assertEquals("svm", body.getFirst("classifier"));
        assertEquals("0.25", body.getFirst("svm_c"));
        assertEquals("true", body.getFirst("auto_tune_svm_c"));
        assertEquals("5", body.getFirst("knn_value"));
        assertNull(body.getFirst("classifierType"));
    }

    @Test
    void forwardsKnnOptionsWithoutChangingClassifier() throws Exception {
        PredictionModelOptions options = PredictionModelOptions.create(
                "knn", 7, true, 1.0, false, null, 2.0);

        MultiValueMap<String, Object> body = new PredictionService().buildRequestBody(
                new byte[0], "target.csv", new MockMultipartFile[0],
                "bug", false, 3, options);

        assertEquals("knn", body.getFirst("classifier_type"));
        assertEquals("knn", body.getFirst("classifier"));
        assertEquals("7", body.getFirst("knn_value"));
        assertEquals("true", body.getFirst("auto_tune_k"));
    }
}
