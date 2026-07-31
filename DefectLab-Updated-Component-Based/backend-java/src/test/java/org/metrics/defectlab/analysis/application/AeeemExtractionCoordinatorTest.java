package org.metrics.defectlab.analysis.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.shared.exception.ExtractionBusyException;

class AeeemExtractionCoordinatorTest {

    @Test
    void allowsOnlyOneAeeemExtractionAcrossApiEntryPoints() {
        AeeemExtractionCoordinator coordinator = new AeeemExtractionCoordinator();

        assertFalse(coordinator.acquire("promise"));
        assertTrue(coordinator.acquire("aeeem"));
        assertThrows(ExtractionBusyException.class,
                () -> coordinator.acquire("AEEEM"));

        coordinator.release(true);
        assertTrue(coordinator.acquire("aeeem"));
        coordinator.release(true);
    }
}
