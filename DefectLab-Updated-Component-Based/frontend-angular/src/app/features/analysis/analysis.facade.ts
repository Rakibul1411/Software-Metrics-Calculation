import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DatasetFamily, DatasetSummary } from '../../core/models/defectlab.model';
import { DefectLabApiService } from '../../core/services/defectlab-api.service';
import { SelectOption } from '../../shared/ui-select/ui-select.model';

export type AnalysisMode = 'archive' | 'github';

/** A published AEEEM benchmark and the repository it was mined from. */
interface BenchmarkProfile {
  name: string;
  version: string;
  url: string;
}

export interface AnalysisRequest {
  mode: AnalysisMode;
  family: DatasetFamily;
  projectName: string;
  projectVersion: string;
  archive: File | null;
  githubUrl: string;
  aeeemProfile: string;
}

const SUPPORTED_ARCHIVES = ['.zip', '.tar', '.tgz', '.tar.gz', '.gz'];

const BENCHMARKS: Record<string, BenchmarkProfile> = {
  jdt: { name: 'JDT', version: '3.4', url: 'https://github.com/eclipse-jdt/eclipse.jdt.core' },
  eq: { name: 'EQ', version: '3.4', url: 'https://github.com/eclipse-equinox/equinox.framework' },
  pde: { name: 'PDE', version: '3.4.1', url: 'https://github.com/eclipse-pde/eclipse.pde' },
  lc: { name: 'LC', version: '2.4.0', url: 'https://github.com/apache/lucene' },
  ml: { name: 'ML', version: '3.1', url: 'https://github.com/eclipse-mylyn/org.eclipse.mylyn' }
};

/**
 * Owns the extraction rules: which source modes a metric family allows,
 * what counts as a usable archive, the AEEEM benchmark presets, and which
 * request a given form state maps to.
 */
@Injectable({ providedIn: 'root' })
export class AnalysisFacade {
  readonly aeeemProfileOptions: SelectOption[] = [
    { value: 'current', label: 'Custom (manual entry)' },
    { value: 'jdt', label: 'JDT 3.4' },
    { value: 'eq', label: 'Equinox 3.4' },
    { value: 'pde', label: 'PDE UI 3.4.1' },
    { value: 'lc', label: 'Lucene 2.4.0' },
    { value: 'ml', label: 'Mylyn 3.1' }
  ];

  constructor(private readonly api: DefectLabApiService) {}

  /** AEEEM needs Git history, so a local archive can never satisfy it. */
  allowsArchive(family: DatasetFamily): boolean {
    return family === 'PROMISE';
  }

  isSupportedArchive(file: File): boolean {
    const filename = file.name.toLowerCase();
    return SUPPORTED_ARCHIVES.some(extension => filename.endsWith(extension));
  }

  /** Prefills name/version/URL from a benchmark preset; blank clears them. */
  presetFor(profile: string): BenchmarkProfile {
    return BENCHMARKS[profile] ?? { name: '', version: '', url: '' };
  }

  canSubmit(request: AnalysisRequest): boolean {
    if (!request.projectVersion.trim()) {
      return false;
    }
    if (request.mode === 'archive') {
      return !!request.archive && this.allowsArchive(request.family);
    }
    return request.githubUrl.trim().startsWith('https://github.com/');
  }

  analyze(request: AnalysisRequest): Observable<DatasetSummary> {
    const shared = {
      projectName: request.projectName,
      projectVersion: request.projectVersion,
      family: request.family
    };
    return request.mode === 'archive'
      ? this.api.analyzeArchive({ ...shared, file: request.archive! })
      : this.api.analyzeGitHub({
          ...shared,
          githubUrl: request.githubUrl,
          aeeemProfile: request.aeeemProfile
        });
  }
}
