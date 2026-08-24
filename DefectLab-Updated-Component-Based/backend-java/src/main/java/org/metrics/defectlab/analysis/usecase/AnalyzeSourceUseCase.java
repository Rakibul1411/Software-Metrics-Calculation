package org.metrics.defectlab.analysis.usecase;

import java.io.IOException;

import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.springframework.web.multipart.MultipartFile;

/** Input port: extract PROMISE/AEEEM metrics from a source archive or GitHub repository. */
public interface AnalyzeSourceUseCase {

    MetricDataset analyze(
            Long userId,
            MultipartFile projectArchive,
            String githubUrl,
            String projectName,
            String projectVersion,
            String familyValue,
            String aeeemProfile) throws IOException;
}
