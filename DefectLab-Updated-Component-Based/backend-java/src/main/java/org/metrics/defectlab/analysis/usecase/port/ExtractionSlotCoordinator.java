package org.metrics.defectlab.analysis.usecase.port;

/** Output port: how use cases limit concurrent source-code extractions. */
public interface ExtractionSlotCoordinator {

    boolean acquire(Long userId, String datasetFormat);

    void release(Long userId, String datasetFormat, boolean acquired);
}
