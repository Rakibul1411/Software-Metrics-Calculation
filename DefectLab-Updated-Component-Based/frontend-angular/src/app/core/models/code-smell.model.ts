export type CodeSmellType =
  | 'GOD_CLASS'
  | 'SPAGHETTI_COUPLING'
  | 'INCOHERENT_MODULE'
  | 'DEEP_HIERARCHY'
  | 'BROAD_HIERARCHY'
  | 'LEAKY_ENCAPSULATION'
  | 'CHURN_VOLATILITY';

export type CodeSmellSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface CodeSmell {
  type: CodeSmellType;
  title: string;
  name?: string;
  severity: CodeSmellSeverity;
  summary: string;
  description?: string;
  recommendation: string;
  metricTrigger: string;
  heuristic?: string;
}

export interface ClassAnalysisResult {
  className: string;
  packageName: string;
  loc: number;
  wmc: number;
  cbo: number;
  lcom: number;
  dit: number;
  rfc: number;
  ca: number; // Afferent coupling (inbound callers)
  ce: number; // Efferent coupling (outbound dependencies)
  instability: number; // Ce / (Ca + Ce)
  maintainabilityIndex: number; // 0 to 100
  maintainabilityRating: 'HIGH' | 'MODERATE' | 'LOW';
  blastRadius: number; // Estimated downstream classes affected
  npm?: number;
  dam?: number;
  noc?: number;
  amc?: number;
  max_cc?: number;
  avg_cc?: number;
  churn?: number;
  entropy?: number;
  smells: CodeSmell[];
  riskScore: number; // 0.0 to 1.0
  predictedLabel?: number;
}
