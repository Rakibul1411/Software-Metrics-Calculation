package org.metrics.defectlab.analysis.usecase.port;

/** Coordinates and limits concurrent source-code metric extraction tasks. */
public interface ExtractionSlotCoordinator {

    boolean acquire(Long userId, String datasetFormat);

    void release(Long userId, String datasetFormat, boolean acquired);
}
