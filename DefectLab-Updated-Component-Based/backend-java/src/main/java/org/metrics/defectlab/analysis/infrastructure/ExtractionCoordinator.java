package org.metrics.defectlab.analysis.infrastructure;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.metrics.defectlab.analysis.usecase.port.ExtractionSlotCoordinator;
import org.metrics.defectlab.shared.exception.ExtractionBusyException;
import org.springframework.stereotype.Service;

/**
 * Prevents a single user from running more than one source analysis of the
 * same family at a time, and additionally caps AEEEM (full git clone plus
 * history walk) to a small number of concurrent extractions system-wide,
 * since it is far more resource-intensive than a PROMISE archive/snapshot
 * extraction. PROMISE has no system-wide cap -- only the per-user guard.
 */
@Service
public final class ExtractionCoordinator implements ExtractionSlotCoordinator {

    private static final int MAX_CONCURRENT_AEEEM = 2;

    private final Semaphore aeeemSlots = new Semaphore(MAX_CONCURRENT_AEEEM, true);
    private final Set<Long> usersExtractingAeeem = ConcurrentHashMap.newKeySet();
    private final Set<Long> usersExtractingPromise = ConcurrentHashMap.newKeySet();

    @Override
    public boolean acquire(Long userId, String datasetFormat) {
        return isAeeem(datasetFormat) ? acquireAeeem(userId) : acquirePromise(userId);
    }

    @Override
    public void release(Long userId, String datasetFormat, boolean acquired) {
        if (!acquired) {
            return;
        }
        if (isAeeem(datasetFormat)) {
            usersExtractingAeeem.remove(userId);
            aeeemSlots.release();
        } else {
            usersExtractingPromise.remove(userId);
        }
    }

    private boolean acquireAeeem(Long userId) {
        if (!usersExtractingAeeem.add(userId)) {
            throw new ExtractionBusyException(
                    "You already have an AEEEM extraction running. "
                    + "Wait for it to finish before starting another.");
        }
        if (!aeeemSlots.tryAcquire()) {
            usersExtractingAeeem.remove(userId);
            throw new ExtractionBusyException(
                    "The server is already running the maximum number of AEEEM "
                    + "extractions. Wait for one to finish before starting another.");
        }
        return true;
    }

    private boolean acquirePromise(Long userId) {
        if (!usersExtractingPromise.add(userId)) {
            throw new ExtractionBusyException(
                    "You already have a PROMISE extraction running. "
                    + "Wait for it to finish before starting another.");
        }
        return true;
    }

    private static boolean isAeeem(String datasetFormat) {
        return datasetFormat != null && "aeeem".equalsIgnoreCase(datasetFormat.trim());
    }
}
