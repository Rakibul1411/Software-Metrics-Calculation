package org.metrics.defectlab.analysis.usecase.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisOptions;
import org.metrics.defectlab.analysis.aeeem.history.AeeemAnalysisSummary;
import org.metrics.defectlab.shared.model.DatasetFileFormat;

/** Output port: how use cases turn Java source directories into PROMISE/AEEEM metrics. */
public interface MetricsExtractor {

    ExtractionResult extractMetrics(
            String sourceDirsStr,
            String datasetFormat,
            String filterFile,
            AeeemAnalysisOptions aeeemOptions) throws IOException;

    Path getDatasetPath(String targetDatasetId, DatasetFileFormat fileFormat);

    /** The outcome of one metric-extraction run: where it landed and what it produced. */
    final class ExtractionResult {
        private final String targetDatasetId;
        private final String datasetFormat;
        private final int rowCount;
        private final List<String> extractedColumns;
        private final AeeemAnalysisSummary aeeemAnalysis;

        public ExtractionResult(String targetDatasetId, String datasetFormat, int rowCount,
                                List<String> extractedColumns,
                                AeeemAnalysisSummary aeeemAnalysis) {
            this.targetDatasetId = targetDatasetId;
            this.datasetFormat = datasetFormat;
            this.rowCount = rowCount;
            this.extractedColumns = extractedColumns;
            this.aeeemAnalysis = aeeemAnalysis;
        }

        public String getTargetDatasetId() { return targetDatasetId; }
        public String getDatasetFormat() { return datasetFormat; }
        public int getRowCount() { return rowCount; }
        public List<String> getExtractedColumns() { return extractedColumns; }
        public AeeemAnalysisSummary getAeeemAnalysis() { return aeeemAnalysis; }
    }
}
