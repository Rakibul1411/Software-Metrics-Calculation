package org.metrics.defectlab.analysis.usecase;

import java.io.IOException;

import org.metrics.defectlab.dataset.domain.MetricDataset;

/** Input port: extract PROMISE/AEEEM metrics from a source archive or GitHub repository. */
public interface AnalyzeSourceUseCase {

    MetricDataset analyze(
            Long userId,
            UploadedArchive projectArchive,
            String githubUrl,
            String projectName,
            String projectVersion,
            String familyValue,
            String aeeemProfile) throws IOException;
}
