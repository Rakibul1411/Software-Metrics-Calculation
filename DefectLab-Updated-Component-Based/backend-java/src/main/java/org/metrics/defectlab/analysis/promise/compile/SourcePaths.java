package org.metrics.defectlab.analysis.promise.compile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * One canonical form for source paths.
 *
 * <p>The compiler reports problems against the real path, so a release staged
 * under a symlinked directory would otherwise never match the paths we hold: on
 * macOS an upload in {@code /var/folders/...} is reported as
 * {@code /private/var/folders/...}. Matching those two is what lets a failed
 * source take its classes out of the output, so both sides go through here.
 */
public final class SourcePaths {

    private SourcePaths() {
    }

    public static Path canonical(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException exception) {
            // The file may not exist yet; the normalised form is the best we have.
            return absolute;
        }
    }
}
