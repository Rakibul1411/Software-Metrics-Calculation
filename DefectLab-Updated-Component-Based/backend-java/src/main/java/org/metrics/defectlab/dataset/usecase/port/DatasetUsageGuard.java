package org.metrics.defectlab.dataset.usecase.port;

/**
 * Output port: whether a dataset is still referenced by another component
 * (a saved prediction run or metric comparison) and therefore cannot be
 * deleted. Owned by {@code dataset} so it never has to depend on
 * {@code prediction}/{@code comparison} directly; a composition-root adapter
 * wires the actual cross-component check.
 */
public interface DatasetUsageGuard {

    boolean isReferencedElsewhere(Long datasetId);
}
