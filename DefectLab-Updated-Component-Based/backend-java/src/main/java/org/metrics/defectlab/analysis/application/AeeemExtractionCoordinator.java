package org.metrics.defectlab.analysis.application;

import java.util.concurrent.Semaphore;

import org.metrics.defectlab.shared.exception.ExtractionBusyException;
import org.springframework.stereotype.Service;

/**
 * Shares the single AEEEM extraction slot across every API that can start the
 * history-intensive analysis.
 */
@Service
public final class AeeemExtractionCoordinator {

    private final Semaphore extractionSlot = new Semaphore(1, true);

    public boolean acquire(String datasetFormat) {
        if (!isAeeem(datasetFormat)) {
            return false;
        }
        if (!extractionSlot.tryAcquire()) {
            throw new ExtractionBusyException(
                    "Another AEEEM repository is already being analyzed. "
                    + "Wait for it to finish before starting a new extraction.");
        }
        return true;
    }

    public void release(boolean acquired) {
        if (acquired) {
            extractionSlot.release();
        }
    }

    private static boolean isAeeem(String datasetFormat) {
        return datasetFormat != null
                && "aeeem".equalsIgnoreCase(datasetFormat.trim());
    }
}
