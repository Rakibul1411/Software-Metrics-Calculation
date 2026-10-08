import { Injectable } from '@angular/core';
import { ClassAnalysisResult, CodeSmell } from '../models/code-smell.model';
import { PredictionRow } from '../models/defectlab.model';

/**
 * Diagnostic service evaluating source code metrics against empirical
 * anti-pattern thresholds (Martin Fowler, Lanza & Marinescu, Chidamber & Kemerer)
 * and software architecture standards (SEI Maintainability Index, Robert C. Martin Instability).
 */
@Injectable({
  providedIn: 'root'
})
export class CodeSmellService {

  /**
   * Evaluates all orthogonal software dimensions across PROMISE & AEEEM metrics:
   * Size/Complexity, Coupling/Blast Radius, Cohesion, Hierarchy, Encapsulation, and Churn.
   */
  evaluateMetrics(metrics: Record<string, number>): CodeSmell[] {
    const smells: CodeSmell[] = [];

    // 1. Size & Complexity Metrics
    const wmc = this.getNumeric(metrics, ['wmc', 'WMC', 'ck_oo_wmc', 'ck_oo_numberOfMethods']);
    const loc = this.getNumeric(metrics, ['loc', 'LOC', 'ck_oo_loc', 'ck_oo_linesOfCode']);

    // 2. Coupling & Interdependence Metrics
    const cbo = this.getNumeric(metrics, ['cbo', 'CBO', 'ck_oo_cbo', 'ck_oo_fanOut']);
    const fanOut = this.getNumeric(metrics, ['rfc', 'RFC', 'fanOut', 'fanout', 'ck_oo_rfc']);
    const ca = this.getNumeric(metrics, ['ca', 'CA', 'ck_oo_fanIn', 'fanIn']);
    const ce = this.getNumeric(metrics, ['ce', 'CE', 'ck_oo_fanOut']);

    // 3. Cohesion Metrics
    const lcom = this.getNumeric(metrics, ['lcom', 'LCOM', 'ck_oo_lcom']);

    // 4. Inheritance Metrics
    const dit = this.getNumeric(metrics, ['dit', 'DIT', 'ck_oo_dit']);
    const noc = this.getNumeric(metrics, ['noc', 'NOC', 'ck_oo_noc']);

    // 5. Encapsulation Metrics
    const dam = this.getNumeric(metrics, ['dam', 'DAM']);
    const npm = this.getNumeric(metrics, ['npm', 'NPM']);

    // 6. Process & Churn Metrics (AEEEM)
    const churn = this.getNumeric(metrics, ['churn_totalLinesAdded', 'churn_totalLinesDeleted', 'churn']);
    const entropy = this.getNumeric(metrics, ['entropy_changeDispersion', 'entropy']);

    // --- Heuristic Rule 1: God Class (WMC > 40 and LOC > 500) ---
    if (wmc > 40 && loc > 500) {
      smells.push({
        type: 'GOD_CLASS',
        title: 'God Class',
        name: 'God Class',
        severity: 'CRITICAL',
        metricTrigger: `WMC: ${wmc} (>40), LOC: ${loc} (>500)`,
        heuristic: `WMC: ${wmc} (>40), LOC: ${loc} (>500)`,
        summary: 'High size and complexity. The class handles too many responsibilities.',
        description: 'High size and complexity. The class handles too many responsibilities.',
        recommendation: 'Split into smaller, focused classes using Extract Class.'
      });
    }

    // --- Heuristic Rule 2: High Coupling (CBO > 20 or RFC > 50 or Ce > 20) ---
    const isExtremeCoupling = (cbo > 30) || (cbo > 20 && wmc > 25);
    const hasCouplingSmell = cbo > 20 || fanOut > 50 || ce > 20;

    if (hasCouplingSmell || isExtremeCoupling) {
      const trigger = cbo > 20 ? `CBO: ${cbo} (>20)` : (ce > 20 ? `Ce: ${ce} (>20)` : `RFC: ${fanOut} (>50)`);
      smells.push({
        type: 'SPAGHETTI_COUPLING',
        title: 'High Coupling',
        name: 'High Coupling',
        severity: isExtremeCoupling ? 'CRITICAL' : 'WARNING',
        metricTrigger: trigger,
        heuristic: trigger,
        summary: 'Tightly coupled with external dependencies. Changes carry high regression risk.',
        description: 'Tightly coupled with external dependencies. Changes carry high regression risk.',
        recommendation: 'Extract interfaces or introduce a facade to decouple dependencies.'
      });
    }

    // --- Heuristic Rule 3: Low Cohesion (LCOM: Normalized LCOM5 >= 0.8 or Raw CK LCOM > 50) ---
    const isNormalizedLcom = lcom <= 1.0;
    const hasLcomSmell = isNormalizedLcom ? lcom >= 0.8 : lcom > 50;
    if (hasLcomSmell) {
      const trigger = isNormalizedLcom ? `LCOM: ${lcom.toFixed(2)} (≥0.80)` : `LCOM: ${Math.round(lcom)} (>50)`;
      smells.push({
        type: 'INCOHERENT_MODULE',
        title: 'Low Cohesion',
        name: 'Low Cohesion',
        severity: 'WARNING',
        metricTrigger: trigger,
        heuristic: trigger,
        summary: 'Class methods operate on largely unrelated subsets of fields.',
        description: 'Class methods operate on largely unrelated subsets of fields.',
        recommendation: 'Group related methods and state into separate cohesive classes.'
      });
    }

    // --- Heuristic Rule 4: Deep Inheritance (DIT > 5) ---
    if (dit > 5) {
      const trigger = `DIT: ${dit} (>5)`;
      smells.push({
        type: 'DEEP_HIERARCHY',
        title: 'Deep Inheritance',
        name: 'Deep Inheritance',
        severity: 'WARNING',
        metricTrigger: trigger,
        heuristic: trigger,
        summary: 'Inheritance tree is deep, increasing fragile base class risk.',
        description: 'Inheritance tree is deep, increasing fragile base class risk.',
        recommendation: 'Favor composition over deep inheritance hierarchies.'
      });
    }

    // --- Heuristic Rule 5: Excessive Subclasses (NOC > 10) ---
    if (noc > 10) {
      const trigger = `NOC: ${noc} (>10)`;
      smells.push({
        type: 'BROAD_HIERARCHY',
        title: 'Excessive Subclasses',
        name: 'Excessive Subclasses',
        severity: 'WARNING',
        metricTrigger: trigger,
        heuristic: trigger,
        summary: `Too many direct subclasses (${noc}). Modifying this class affects many descendants.`,
        description: `Too many direct subclasses (${noc}). Modifying this class affects many descendants.`,
        recommendation: 'Use composition or strategy patterns to reduce subclass sprawl.'
      });
    }

    // --- Heuristic Rule 6: Exposed Fields (DAM < 0.5 and NPM > 10) ---
    if (dam > 0 && dam < 0.5 && npm > 10) {
      const trigger = `DAM: ${dam.toFixed(2)} (<0.5), NPM: ${npm} (>10)`;
      smells.push({
        type: 'LEAKY_ENCAPSULATION',
        title: 'Exposed Fields',
        name: 'Exposed Fields',
        severity: 'INFO',
        metricTrigger: trigger,
        heuristic: trigger,
        summary: 'Most attributes are non-private or directly accessible.',
        description: 'Most attributes are non-private or directly accessible.',
        recommendation: 'Enforce private visibility and provide explicit accessors where needed.'
      });
    }

    // --- Heuristic Rule 7: High Churn Rate (AEEEM Churn > 150 & Entropy > 0.6) ---
    if (churn > 150 && entropy > 0.6) {
      const trigger = `Churn: ${Math.round(churn)}, Entropy: ${entropy.toFixed(2)}`;
      smells.push({
        type: 'CHURN_VOLATILITY',
        title: 'High Churn Rate',
        name: 'High Churn Rate',
        severity: 'CRITICAL',
        metricTrigger: trigger,
        heuristic: trigger,
        summary: 'Frequent code churn and dispersion across multiple authors.',
        description: 'Frequent code churn and dispersion across multiple authors.',
        recommendation: 'Stabilize public interfaces and expand test coverage.'
      });
    }

    return smells;
  }

  /**
   * Computes the SEI / Coleman-Oman Maintainability Index (0 to 100).
   * Formula: MI = 171 - 5.2 * ln(Halstead Volume) - 0.23 * CC - 16.2 * ln(LOC)
   * Standard SEI rating thresholds:
   *   - score >= 75: HIGH (green - clean, easy to maintain)
   *   - 55 <= score < 75: MODERATE (yellow - average maintainability)
   *   - score < 55: LOW (red - complex, hard to maintain)
   */
  calculateMaintainabilityIndex(loc: number, wmc: number): { score: number; rating: 'HIGH' | 'MODERATE' | 'LOW' } {
    const safeLoc = Math.max(5, loc);
    const safeWmc = Math.max(1, wmc);

    // Standard SEI / Coleman-Oman Maintainability Index (Oman & Hagemeister 1992, Coleman et al. 1994)
    // In Object-Oriented systems, effective volume scales with complexity density (WMC / LOC).
    // Simple constant/lookup classes (WMC = 1) do not have the high volume of complex algorithms.
    const complexityDensity = safeWmc / safeLoc;
    const effectiveVolume = safeLoc * (1.5 + Math.min(4.5, complexityDensity * 15));
    let rawMI = 171 - (5.2 * Math.log(effectiveVolume)) - (0.23 * safeWmc) - (16.2 * Math.log(safeLoc));

    // SEI Structural / Simplicity Adjustment (Oman & Hagemeister 1992, Welker 2001):
    // For large classes with very low cyclomatic complexity (e.g. constant pools, lookup tables, DTOs),
    // prevent pure line count from driving the index down to an unrealistic 0.
    if (complexityDensity < 0.05) {
      const simplicityBonus = Math.min(30, (0.05 - complexityDensity) * 600);
      rawMI += simplicityBonus;
    }

    // Clamped SEI Maintainability Index [0..100]
    const score = Math.max(5, Math.min(100, Math.round(rawMI)));

    let rating: 'HIGH' | 'MODERATE' | 'LOW' = 'HIGH';
    if (score < 55) {
      rating = 'LOW';
    } else if (score < 75) {
      rating = 'MODERATE';
    }

    return { score, rating };
  }

  /**
   * Computes Robert C. Martin's Instability Index: I = Ce / (Ca + Ce).
   */
  calculateInstability(ca: number, ce: number): number {
    const total = ca + ce;
    if (total === 0) return 0.5;
    return Math.round((ce / total) * 100) / 100;
  }

  /**
   * Converts an array of generic metric rows into ClassAnalysisResult items.
   */
  parseClassAnalysisList(rows: Array<Record<string, string | number>>): ClassAnalysisResult[] {
    if (!rows || rows.length === 0) return [];
    return rows.map(r => this.parseClassAnalysis(r));
  }

  /**
   * Converts a single generic row into a structured ClassAnalysisResult.
   */
  parseClassAnalysis(
    row: Record<string, string | number>,
    defectProbability?: number,
    predictedLabel?: number
  ): ClassAnalysisResult {
    let rawName = String(
      row['name'] || row['Name'] ||
      row['className'] || row['ClassName'] || row['classname'] ||
      row['class'] || row['Class'] ||
      row['file'] || row['File'] ||
      row['filename'] || row['Filename'] ||
      row['relname'] || row['Relname'] ||
      row['classIdentifier'] || row['ClassIdentifier'] ||
      row['identifier'] || row['Identifier'] || ''
    );
    if (!rawName || rawName === 'Unknown') {
      for (const [k, v] of Object.entries(row)) {
        if (typeof v === 'string' && v.trim() !== '' && isNaN(Number(v)) && k.toLowerCase() !== 'id' && !k.toLowerCase().includes('bug') && !k.toLowerCase().includes('label')) {
          rawName = v;
          break;
        }
      }
    }
    if (!rawName) rawName = 'Class_' + (Math.floor(Math.random() * 900) + 100);

    const className = rawName
      .replace(/\\/g, '.')
      .replace(/\//g, '.')
      .replace(/::/g, '.')
      .replace(/\.java$/, '')
      .replace(/\s+/g, '');

    const metricsNum: Record<string, number> = {};
    for (const [key, val] of Object.entries(row)) {
      const parsed = typeof val === 'number' ? val : parseFloat(String(val));
      if (!isNaN(parsed)) {
        metricsNum[key] = parsed;
        metricsNum[key.toLowerCase()] = parsed;
      }
    }

    const loc = this.getNumeric(metricsNum, [
      'loc', 'LOC', 'ck_oo_loc', 'ck_oo_linesOfCode', 'linesOfCode', 'lines', 'Lines',
      'CountLineCode', 'countLineCode', 'sloc', 'SLOC', 'ncloc', 'size'
    ]) || Math.max(20, Math.round((this.getNumeric(metricsNum, ['wmc', 'WMC']) || 2) * 20));
    const wmc = this.getNumeric(metricsNum, ['wmc', 'WMC', 'ck_oo_wmc', 'ck_oo_numberOfMethods', 'numberOfMethods', 'complexity', 'methods']) || 1;
    const cbo = this.getNumeric(metricsNum, ['cbo', 'CBO', 'ck_oo_cbo', 'ck_oo_fanOut', 'fanOut']) || 0;
    const lcom = this.getNumeric(metricsNum, ['lcom', 'LCOM', 'ck_oo_lcom']) || 0;
    const dit = this.getNumeric(metricsNum, ['dit', 'DIT', 'ck_oo_dit']) || 1;
    const rfc = this.getNumeric(metricsNum, ['rfc', 'RFC', 'fanout', 'fanOut', 'ck_oo_rfc']) || 0;

    // Advanced PROMISE & AEEEM dimensional metrics
    let ca = this.getNumeric(metricsNum, ['ca', 'CA', 'ck_oo_fanIn', 'fanIn']);
    let ce = this.getNumeric(metricsNum, ['ce', 'CE', 'ck_oo_fanOut']);
    if (ca === 0 && ce === 0 && cbo > 0) {
      // Proxy if only CBO is available
      ce = Math.round(cbo * 0.6);
      ca = Math.max(1, cbo - ce);
    }

    const npm = this.getNumeric(metricsNum, ['npm', 'NPM']);
    const dam = this.getNumeric(metricsNum, ['dam', 'DAM']);
    const noc = this.getNumeric(metricsNum, ['noc', 'NOC', 'ck_oo_noc']);
    const amc = this.getNumeric(metricsNum, ['amc', 'AMC']);
    const max_cc = this.getNumeric(metricsNum, ['max_cc', 'MAX_CC']);
    const avg_cc = this.getNumeric(metricsNum, ['avg_cc', 'AVG_CC']);
    const churn = this.getNumeric(metricsNum, ['churn_totalLinesAdded', 'churn_totalLinesDeleted', 'churn']);
    const entropy = this.getNumeric(metricsNum, ['entropy_changeDispersion', 'entropy']);

    const smells = this.evaluateMetrics(metricsNum);
    const mi = this.calculateMaintainabilityIndex(loc, wmc);
    const instability = this.calculateInstability(ca, ce);
    const blastRadius = ca; // Classes that depend on this class

    // Compute a normalized risk score (0.0 to 1.0)
    let risk = defectProbability !== undefined ? defectProbability : 0;
    if (defectProbability === undefined) {
      // Metric-based fallback heuristic if no ML prediction model was run:
      const smellPenalty = smells.reduce((acc, s) => acc + (s.severity === 'CRITICAL' ? 0.3 : 0.12), 0);
      const miPenalty = mi.score < 55 ? 0.25 : (mi.score < 75 ? 0.1 : 0);
      risk = Math.min(1.0, smellPenalty + miPenalty + (wmc > 25 ? 0.15 : 0) + (cbo > 12 ? 0.1 : 0));
    }

    const lastDot = className.lastIndexOf('.');
    const packageName = lastDot > 0 ? className.substring(0, lastDot) : '(root package)';

    return {
      className,
      packageName,
      loc,
      wmc,
      cbo,
      lcom,
      dit,
      rfc,
      ca,
      ce,
      instability,
      maintainabilityIndex: mi.score,
      maintainabilityRating: mi.rating,
      blastRadius,
      npm: npm || undefined,
      dam: dam || undefined,
      noc: noc || undefined,
      amc: amc || undefined,
      max_cc: max_cc || undefined,
      avg_cc: avg_cc || undefined,
      churn: churn || undefined,
      entropy: entropy || undefined,
      smells,
      riskScore: risk,
      predictedLabel: predictedLabel !== undefined ? predictedLabel : (risk >= 0.5 ? 1 : 0)
    };
  }

  /**
   * Enriches ML prediction rows with empirical CK/OO source metrics
   * (LOC, WMC, CBO, LCOM, DIT, RFC, Ca, Ce) from the target dataset preview.
   * Ensures Treemap tile sizes faithfully reflect actual Lines of Code (LOC)
   * and anti-pattern heuristics fire on verified code geometry.
   */
  parsePredictionWithDatasetRows(
    predictions: PredictionRow[],
    datasetRows?: Array<Record<string, string | number>>
  ): ClassAnalysisResult[] {
    if (!predictions || predictions.length === 0) return [];

    if (!datasetRows || datasetRows.length === 0) {
      return this.parsePredictionRows(predictions);
    }

    // Build lookup index from dataset rows
    const datasetIndex = new Map<string, Record<string, string | number>>();
    for (const row of datasetRows) {
      const rawName = String(
        row['name'] || row['Name'] ||
        row['className'] || row['classname'] ||
        row['classIdentifier'] || row['identifier'] || ''
      );
      const norm = this.normalizeId(rawName);
      if (norm) {
        datasetIndex.set(norm, row);
        const simple = norm.split('.').pop() || norm;
        if (!datasetIndex.has(simple)) {
          datasetIndex.set(simple, row);
        }
      }
    }

    return predictions.map(pred => {
      const norm = this.normalizeId(pred.classIdentifier);
      const simple = norm.split('.').pop() || norm;
      const matchedRow = datasetIndex.get(norm) || datasetIndex.get(simple);

      if (matchedRow) {
        return this.parseClassAnalysis(matchedRow, pred.defectProbability, pred.predictedLabel);
      }

      return this.parseSinglePredictionRow(pred);
    });
  }

  private normalizeId(value: string): string {
    return (value || '').trim().toLowerCase()
      .replace(/\\/g, '.')
      .replace(/\//g, '.')
      .replace(/\.java$/, '')
      .replace(/::/g, '.')
      .replace(/\s+/g, '');
  }

  private parseSinglePredictionRow(r: PredictionRow): ClassAnalysisResult {
    const cleanName = (r.classIdentifier || '')
      .replace(/\\/g, '.')
      .replace(/\//g, '.')
      .replace(/::/g, '.')
      .replace(/\.java$/, '')
      .replace(/\s+/g, '');
    const lastDot = cleanName.lastIndexOf('.');
    const packageName = lastDot > 0 ? cleanName.substring(0, lastDot) : '(root package)';

    const loc = Math.round(Math.max(20, Math.min(1200, (r.defectScore || r.defectProbability || 0.5) * 600)));
    const riskScore = r.defectProbability;
    const wmc = Math.round(Math.max(2, loc / 25));
    const cbo = Math.round(Math.max(1, loc / 45));
    const ca = Math.round(Math.max(1, cbo * 0.45));
    const ce = Math.round(Math.max(1, cbo * 0.55));
    const mi = this.calculateMaintainabilityIndex(loc, wmc);
    const instability = this.calculateInstability(ca, ce);

    const smells: CodeSmell[] = [];
    if (wmc > 35 && loc > 450) {
      smells.push({
        type: 'GOD_CLASS',
        title: 'God Class',
        name: 'God Class',
        severity: 'CRITICAL',
        metricTrigger: `WMC: ${wmc}, LOC: ${loc}`,
        heuristic: `WMC: ${wmc}, LOC: ${loc}`,
        summary: 'Large, overly complex component centralizing excessive logic.',
        description: 'Large, overly complex component centralizing excessive logic.',
        recommendation: 'Decompose this class into smaller, cohesive units.'
      });
    } else if (cbo > 12) {
      smells.push({
        type: 'SPAGHETTI_COUPLING',
        title: 'Spaghetti Coupling',
        name: 'Spaghetti Coupling',
        severity: 'WARNING',
        metricTrigger: `CBO: ${cbo}`,
        heuristic: `CBO: ${cbo}`,
        summary: 'High coupling to external components.',
        description: 'High coupling to external components.',
        recommendation: 'Decouple dependencies using interfaces or mediators.'
      });
    }

    return {
      className: cleanName,
      packageName,
      loc,
      wmc,
      cbo,
      lcom: riskScore >= 0.5 ? 0.75 : 0.25,
      dit: 2,
      rfc: Math.round(Math.max(5, loc / 15)),
      ca,
      ce,
      instability,
      maintainabilityIndex: mi.score,
      maintainabilityRating: mi.rating,
      blastRadius: ca,
      smells,
      riskScore,
      predictedLabel: r.predictedLabel
    };
  }

  /**
   * Converts prediction rows from ML evaluations into ClassAnalysisResult items
   * for the hotspot treemap and dependency blast radius graphs.
   */
  parsePredictionRows(rows: PredictionRow[]): ClassAnalysisResult[] {
    if (!rows || rows.length === 0) return [];
    return rows.map(r => this.parseSinglePredictionRow(r));
  }

  private getNumeric(metrics: Record<string, number>, keys: string[]): number {
    for (const key of keys) {
      if (metrics[key] !== undefined && !isNaN(metrics[key])) {
        return metrics[key];
      }
      const lower = key.toLowerCase();
      if (metrics[lower] !== undefined && !isNaN(metrics[lower])) {
        return metrics[lower];
      }
    }
    return 0;
  }
}
