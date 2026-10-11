import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';
import {
  CoralTsnePoint,
  CoralTsneResponse,
  DomainStats
} from '../../core/models/defectlab.model';

export type CoralDisplayMode = 'side-by-side' | 'before' | 'after' | 'overlay';
export type CoralLabelFilter = 'all' | 'buggy' | 'clean';

export interface ShiftVector {
  id: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface ScaledPoint extends CoralTsnePoint {
  svgX: number;
  svgY: number;
}

export interface ScaledDomainStats {
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  raw: DomainStats;
}

export interface PlotGeometry {
  width: number;
  height: number;
  padLeft: number;
  padRight: number;
  padTop: number;
  padBottom: number;
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
  xTicks: { value: number; svgX: number }[];
  yTicks: { value: number; svgY: number }[];
}

@Component({
  selector: 'ui-coral-distribution',
  standalone: false,
  templateUrl: './ui-coral-distribution.component.html'
})
export class UiCoralDistributionComponent implements OnInit, OnChanges {
  @Input() data: CoralTsneResponse | null = null;
  @Input() loading = false;
  @Input() sourceDatasetName = 'Source Dataset';
  @Input() targetDatasetName = 'Target Dataset';
  @Output() readonly reload = new EventEmitter<void>();

  displayMode: CoralDisplayMode = 'side-by-side';
  labelFilter: CoralLabelFilter = 'all';
  showShiftVectors = true;

  hoveredPoint: ScaledPoint | null = null;
  tooltipPos = { x: 0, y: 0 };

  // Plot geometries
  dualWidth = 560;
  dualHeight = 420;
  singleWidth = 920;
  singleHeight = 520;

  geomBefore!: PlotGeometry;
  geomAfter!: PlotGeometry;
  geomOverlay!: PlotGeometry;

  scaledBeforePoints: ScaledPoint[] = [];
  scaledAfterPoints: ScaledPoint[] = [];
  scaledOverlaySourcePoints: ScaledPoint[] = [];
  scaledOverlayAlignedPoints: ScaledPoint[] = [];
  scaledOverlayTargetPoints: ScaledPoint[] = [];
  overlayShiftVectors: ShiftVector[] = [];

  statsBeforeSource: ScaledDomainStats | null = null;
  statsBeforeTarget: ScaledDomainStats | null = null;
  statsAfterSource: ScaledDomainStats | null = null;
  statsAfterTarget: ScaledDomainStats | null = null;

  ngOnInit(): void {
    if (this.data) {
      this.recalculatePlots();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data'] && this.data) {
      this.recalculatePlots();
    }
  }

  setDisplayMode(mode: CoralDisplayMode): void {
    this.displayMode = mode;
    this.recalculatePlots();
  }

  setLabelFilter(filter: CoralLabelFilter): void {
    this.labelFilter = filter;
  }

  recalculatePlots(): void {
    if (!this.data) return;

    const isDual = this.displayMode === 'side-by-side';
    const w = isDual ? this.dualWidth : this.singleWidth;
    const h = isDual ? this.dualHeight : this.singleHeight;

    // 1. Before plot
    this.geomBefore = this.calculateGeometry(this.data.before, w, h);
    this.scaledBeforePoints = this.scalePoints(this.data.before, this.geomBefore);

    if (this.data.stats) {
      this.statsBeforeSource = this.scaleStats(this.data.stats.beforeSource, this.geomBefore);
      this.statsBeforeTarget = this.scaleStats(this.data.stats.beforeTarget, this.geomBefore);
    }

    // 2. After plot
    this.geomAfter = this.calculateGeometry(this.data.after, w, h);
    this.scaledAfterPoints = this.scalePoints(this.data.after, this.geomAfter);

    if (this.data.stats) {
      this.statsAfterSource = this.scaleStats(this.data.stats.afterSource, this.geomAfter);
      this.statsAfterTarget = this.scaleStats(this.data.stats.afterTarget, this.geomAfter);
    }

    // 3. Overlay plot (combining before source + after aligned + after target)
    const overlayPts: CoralTsnePoint[] = [
      ...this.data.before.filter(p => p.domain === 'source'),
      ...this.data.after
    ];
    this.geomOverlay = this.calculateGeometry(overlayPts, this.singleWidth, this.singleHeight);
    this.scaledOverlaySourcePoints = this.scalePoints(
      this.data.before.filter(p => p.domain === 'source'),
      this.geomOverlay
    );
    this.scaledOverlayAlignedPoints = this.scalePoints(
      this.data.after.filter(p => p.domain === 'source_aligned'),
      this.geomOverlay
    );
    this.scaledOverlayTargetPoints = this.scalePoints(
      this.data.after.filter(p => p.domain === 'target'),
      this.geomOverlay
    );

    // Build shift vectors connecting each original source point to its aligned counterpart
    const alignedMap = new Map(this.scaledOverlayAlignedPoints.map(p => [p.id, p]));
    this.overlayShiftVectors = this.scaledOverlaySourcePoints
      .map(sp => {
        const ap = alignedMap.get(sp.id);
        if (!ap) return null;
        return {
          id: sp.id,
          x1: sp.svgX,
          y1: sp.svgY,
          x2: ap.svgX,
          y2: ap.svgY
        };
      })
      .filter((v): v is ShiftVector => v !== null);
  }

  toggleShiftVectors(): void {
    this.showShiftVectors = !this.showShiftVectors;
  }

  isVectorVisible(vec: ShiftVector): boolean {
    if (this.labelFilter === 'all') return true;
    const sp = this.scaledOverlaySourcePoints.find(p => p.id === vec.id);
    if (!sp) return true;
    return this.isPointVisible(sp);
  }

  private calculateGeometry(points: CoralTsnePoint[], width: number, height: number): PlotGeometry {
    const padLeft = 48;
    const padRight = 24;
    const padTop = 28;
    const padBottom = 44;

    if (!points || points.length === 0) {
      return {
        width, height, padLeft, padRight, padTop, padBottom,
        minX: -10, maxX: 10, minY: -10, maxY: 10,
        xTicks: [], yTicks: []
      };
    }

    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;

    for (const p of points) {
      if (p.x < minX) minX = p.x;
      if (p.x > maxX) maxX = p.x;
      if (p.y < minY) minY = p.y;
      if (p.y > maxY) maxY = p.y;
    }

    const spanX = Math.max(1, maxX - minX);
    const spanY = Math.max(1, maxY - minY);
    const padX = spanX * 0.12;
    const padY = spanY * 0.12;

    minX -= padX;
    maxX += padX;
    minY -= padY;
    maxY += padY;

    const xTicks = this.generateTicks(minX, maxX, 5).map(val => ({
      value: Math.round(val),
      svgX: padLeft + ((val - minX) / (maxX - minX)) * (width - padLeft - padRight)
    }));

    const yTicks = this.generateTicks(minY, maxY, 5).map(val => ({
      value: Math.round(val),
      svgY: padTop + ((maxY - val) / (maxY - minY)) * (height - padTop - padBottom)
    }));

    return {
      width, height, padLeft, padRight, padTop, padBottom,
      minX, maxX, minY, maxY,
      xTicks, yTicks
    };
  }

  private generateTicks(min: number, max: number, count: number): number[] {
    const step = (max - min) / count;
    const ticks: number[] = [];
    for (let i = 0; i <= count; i++) {
      ticks.push(min + i * step);
    }
    return ticks;
  }

  private scalePoints(points: CoralTsnePoint[], geom: PlotGeometry): ScaledPoint[] {
    const plotW = geom.width - geom.padLeft - geom.padRight;
    const plotH = geom.height - geom.padTop - geom.padBottom;
    const spanX = Math.max(0.0001, geom.maxX - geom.minX);
    const spanY = Math.max(0.0001, geom.maxY - geom.minY);

    return points.map(p => ({
      ...p,
      svgX: geom.padLeft + ((p.x - geom.minX) / spanX) * plotW,
      svgY: geom.padTop + ((geom.maxY - p.y) / spanY) * plotH
    }));
  }

  private scaleStats(stats: DomainStats, geom: PlotGeometry): ScaledDomainStats {
    const plotW = geom.width - geom.padLeft - geom.padRight;
    const plotH = geom.height - geom.padTop - geom.padBottom;
    const spanX = Math.max(0.0001, geom.maxX - geom.minX);
    const spanY = Math.max(0.0001, geom.maxY - geom.minY);

    return {
      cx: geom.padLeft + ((stats.centerX - geom.minX) / spanX) * plotW,
      cy: geom.padTop + ((geom.maxY - stats.centerY) / spanY) * plotH,
      rx: Math.max(12, (stats.radiusX / spanX) * plotW),
      ry: Math.max(12, (stats.radiusY / spanY) * plotH),
      raw: stats
    };
  }

  centroidDistance(s: ScaledDomainStats | null, t: ScaledDomainStats | null): number {
    if (!s || !t) return 0;
    const dx = s.raw.centerX - t.raw.centerX;
    const dy = s.raw.centerY - t.raw.centerY;
    return Math.sqrt(dx * dx + dy * dy);
  }

  midX(s: ScaledDomainStats | null, t: ScaledDomainStats | null): number {
    if (!s || !t) return 0;
    return (s.cx + t.cx) / 2;
  }

  midY(s: ScaledDomainStats | null, t: ScaledDomainStats | null): number {
    if (!s || !t) return 0;
    return (s.cy + t.cy) / 2 - 8;
  }

  isPointVisible(p: ScaledPoint): boolean {
    if (this.labelFilter === 'all') return true;
    if (this.labelFilter === 'buggy') return p.label === 1;
    if (this.labelFilter === 'clean') return p.label === 0;
    return true;
  }

  onPointHover(event: MouseEvent, point: ScaledPoint): void {
    this.hoveredPoint = point;
    const clientX = event.clientX;
    const clientY = event.clientY;

    const tooltipWidth = 280;
    const tooltipHeight = 110;
    const pad = 14;

    let x = clientX + pad;
    if (x + tooltipWidth > window.innerWidth - 12) {
      x = clientX - tooltipWidth - pad;
    }

    let y = clientY + pad;
    if (y + tooltipHeight > window.innerHeight - 12) {
      y = clientY - tooltipHeight - pad;
    }

    x = Math.max(10, Math.min(x, window.innerWidth - tooltipWidth - 10));
    y = Math.max(10, Math.min(y, window.innerHeight - tooltipHeight - 10));

    this.tooltipPos = { x, y };
  }

  onPointLeave(): void {
    this.hoveredPoint = null;
  }

  get sourceBeforeCount(): number {
    return this.data?.before.filter(p => p.domain === 'source').length ?? 0;
  }

  get targetCount(): number {
    return this.data?.before.filter(p => p.domain === 'target').length ?? 0;
  }

  get targetCleanCount(): number {
    return this.data?.before.filter(p => p.domain === 'target' && p.label === 0).length ?? 0;
  }

  get targetBuggyCount(): number {
    return this.data?.before.filter(p => p.domain === 'target' && p.label === 1).length ?? 0;
  }

  get targetUnknownCount(): number {
    return this.data?.before.filter(p => p.domain === 'target' && (p.label === null || p.label === undefined)).length ?? 0;
  }

  get sourceAlignedCount(): number {
    return this.data?.after.filter(p => p.domain === 'source_aligned').length ?? 0;
  }
}
