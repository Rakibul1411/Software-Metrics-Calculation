package org.metrics.defectlab.prediction.usecase.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Output port: renders a downloadable prediction report, free of any PDF-library detail. */
public interface PredictionReportRenderer {

    void write(Path target, String title, List<String> lines) throws IOException;
}
