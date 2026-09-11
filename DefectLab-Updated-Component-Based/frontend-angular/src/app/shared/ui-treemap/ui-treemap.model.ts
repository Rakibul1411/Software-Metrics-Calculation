import { ClassAnalysisResult, CodeSmell } from '../../core/models/code-smell.model';

export interface TreemapCell {
  id: string;
  name: string;
  simpleName: string;
  packageName: string;
  size: number;
  riskScore: number;
  predictedLabel?: number;
  smells: CodeSmell[];
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
  data: ClassAnalysisResult;
}

export interface TreemapGroup {
  packageName: string;
  cells: TreemapCell[];
  x: number;
  y: number;
  width: number;
  height: number;
  totalSize: number;
  hotspotCount?: number;
}
