package org.metrics.aeeem.history;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Added-plus-deleted Java lines for one history interval. */
public final class GitChangePeriod {

    private final int index;
    private final Map<String, Double> changedLinesByPath;

    public GitChangePeriod(int index, Map<String, Double> changedLinesByPath) {
        this.index = index;
        this.changedLinesByPath = Collections.unmodifiableMap(
                new LinkedHashMap<>(changedLinesByPath));
    }

    public int getIndex() {
        return index;
    }

    public Map<String, Double> getChangedLinesByPath() {
        return changedLinesByPath;
    }
}
