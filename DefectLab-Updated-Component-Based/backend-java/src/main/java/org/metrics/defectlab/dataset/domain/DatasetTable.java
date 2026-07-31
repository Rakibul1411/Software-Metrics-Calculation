package org.metrics.defectlab.dataset.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A parsed dataset: canonical headers plus raw string cells, no numeric coercion yet. */
public final class DatasetTable {

    private final List<String> headers;
    private final List<List<String>> rows;

    public DatasetTable(List<String> headers, List<List<String>> rows) {
        this.headers = Collections.unmodifiableList(new ArrayList<>(headers));
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rows.size();
    }

    public int indexOf(String header) {
        return headers.indexOf(header);
    }

    public List<String> column(String header) {
        int index = indexOf(header);
        List<String> values = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            values.add(index < 0 || index >= row.size() ? "" : row.get(index));
        }
        return values;
    }
}
