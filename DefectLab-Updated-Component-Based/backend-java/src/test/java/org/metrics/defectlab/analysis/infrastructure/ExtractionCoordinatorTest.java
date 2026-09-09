package org.metrics.defectlab.analysis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metrics.defectlab.shared.exception.ExtractionBusyException;

class ExtractionCoordinatorTest {

    @Test
    void aSecondPromiseExtractionByTheSameUserIsRejectedButOtherUsersAreNot() {
        ExtractionCoordinator coordinator = new ExtractionCoordinator();

        assertTrue(coordinator.acquire(1L, "promise"));
        assertThrows(ExtractionBusyException.class,
                () -> coordinator.acquire(1L, "PROMISE"));
        assertTrue(coordinator.acquire(2L, "promise"));

        coordinator.release(1L, "promise", true);
        assertTrue(coordinator.acquire(1L, "promise"));
        coordinator.release(1L, "promise", true);
        coordinator.release(2L, "promise", true);
    }

    @Test
    void aSecondAeeemExtractionByTheSameUserIsRejectedEvenWithSlotsFree() {
        ExtractionCoordinator coordinator = new ExtractionCoordinator();

        assertTrue(coordinator.acquire(1L, "aeeem"));
        assertThrows(ExtractionBusyException.class,
                () -> coordinator.acquire(1L, "AEEEM"));

        coordinator.release(1L, "aeeem", true);
        assertTrue(coordinator.acquire(1L, "aeeem"));
        coordinator.release(1L, "aeeem", true);
    }

    @Test
    void aeeemIsCappedGloballyAcrossDifferentUsersOnceSlotsAreExhausted() {
        ExtractionCoordinator coordinator = new ExtractionCoordinator();

        assertTrue(coordinator.acquire(1L, "aeeem"));
        assertTrue(coordinator.acquire(2L, "aeeem"));
        assertThrows(ExtractionBusyException.class,
                () -> coordinator.acquire(3L, "aeeem"));

        coordinator.release(1L, "aeeem", true);
        assertTrue(coordinator.acquire(3L, "aeeem"));

        coordinator.release(2L, "aeeem", true);
        coordinator.release(3L, "aeeem", true);
    }

    @Test
    void promiseAndAeeemSlotsAreIndependentForTheSameUser() {
        ExtractionCoordinator coordinator = new ExtractionCoordinator();

        assertTrue(coordinator.acquire(1L, "promise"));
        assertTrue(coordinator.acquire(1L, "aeeem"));

        coordinator.release(1L, "promise", true);
        coordinator.release(1L, "aeeem", true);
    }

    @Test
    void releaseIsANoOpWhenNothingWasAcquired() {
        ExtractionCoordinator coordinator = new ExtractionCoordinator();
        coordinator.release(1L, "promise", false);
        assertTrue(coordinator.acquire(1L, "promise"));
        coordinator.release(1L, "promise", true);
    }
}
