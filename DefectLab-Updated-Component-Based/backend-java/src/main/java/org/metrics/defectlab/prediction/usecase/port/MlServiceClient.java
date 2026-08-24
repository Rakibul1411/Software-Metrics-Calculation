package org.metrics.defectlab.prediction.usecase.port;

import java.util.Map;

/** Output port: how use cases talk to the internal ML prediction service. */
public interface MlServiceClient {

    Map<String, Object> predict(Map<String, Object> request);

    Map<String, Object> evaluate(Map<String, Object> request);

    class MlServiceException extends RuntimeException {
        public MlServiceException(String message) {
            super(message);
        }
    }
}
