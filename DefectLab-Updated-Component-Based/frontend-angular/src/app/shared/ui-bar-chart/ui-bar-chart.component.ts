import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  ViewChild
} from '@angular/core';
import {
  ChartData,
  ChartMark,
  ChartTick,
  ChartValueFormat
} from './ui-bar-chart.model';

/** Fallback width until the container has been measured. */
const FALLBACK_WIDTH = 640;
const MIN_WIDTH = 280;
/** Bars are capped rather than filling their band — the leftover is air. */
const BAR_MAX = 24;
/** The surface-coloured gap that separates touching marks. */
const GAP = 2;
const AXIS_GUTTER = 30;
/** Widest column band before the plot stops stretching. */
const BAND_MAX = 96;

/**
 * The application's bar chart: horizontal or vertical, grouped or stacked.
 *
 * The viewBox is measured from the container so one SVG unit is one rendered
 * pixel: a 24px bar cap and 11px axis type mean the same thing in a narrow
 * panel and a wide one, which a fixed viewBox scaled away. Geometry is
 * computed on input and on resize rather than in template getters, so change
 * detection never re-runs the layout. Series keep a fixed slot number —
 * colour follows the series, never its size — and every mark carries its own
 * hover/focus target so a value is reachable without reading the axis.
 */
@Component({
  selector: 'ui-bar-chart',
  standalone: false,
  templateUrl: './ui-bar-chart.component.html'
})
export class UiBarChartComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input({ required: true }) data: ChartData = { categories: [], series: [] };
  @Input() orientation: 'horizontal' | 'vertical' = 'horizontal';
  @Input() mode: 'grouped' | 'stacked' = 'grouped';
  @Input() valueFormat: ChartValueFormat = 'integer';
  /** Stacks each category to 100% instead of to the largest total. */
  @Input() normalize = false;
  /** Width reserved for horizontal category names, in user units. */
  @Input() categoryWidth = 150;
  /** Accessible summary of what the chart plots. */
  @Input() caption = '';

  @ViewChild('plot') private plot?: ElementRef<HTMLElement>;

  viewWidth = FALLBACK_WIDTH;
  viewHeight = 240;

  private observer?: ResizeObserver;

  marks: ChartMark[] = [];
  ticks: ChartTick[] = [];
  categoryTicks: ChartTick[] = [];
  hovered: ChartMark | null = null;

  get showLegend(): boolean {
    return this.data.series.length > 1;
  }

  /** Tooltip anchor, as a percentage of the plot so it scales with the SVG. */
  get tooltipLeft(): number {
    const mark = this.hovered;
    return mark ? ((mark.x + mark.width / 2) / this.viewWidth) * 100 : 0;
  }

  get tooltipTop(): number {
    const mark = this.hovered;
    return mark ? (mark.y / this.viewHeight) * 100 : 0;
  }

  constructor(private readonly zone: NgZone) {}

  ngOnChanges(): void {
    this.hovered = null;
    this.layout();
  }

  ngAfterViewInit(): void {
    const element = this.plot?.nativeElement;
    if (!element) {
      return;
    }
    // Outside Angular so a resize storm does not run change detection per frame.
    this.zone.runOutsideAngular(() => {
      this.observer = new ResizeObserver(entries => {
        const width = Math.round(entries[0].contentRect.width);
        if (width && Math.abs(width - this.viewWidth) > 1) {
          this.zone.run(() => {
            this.viewWidth = Math.max(MIN_WIDTH, width);
            this.layout();
          });
        }
      });
      this.observer.observe(element);
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  format(value: number): string {
    if (this.valueFormat === 'percent') {
      return `${Math.round(value * 100)}%`;
    }
    if (this.valueFormat === 'decimal') {
      return value.toFixed(3);
    }
    return value.toLocaleString('en-US');
  }

  /** The legend mirrors the marks, so it asks for the same slot. */
  legendSlot(index: number): number | 'alert' {
    return this.slotOf(index);
  }

  trackMark = (_: number, mark: ChartMark): string => mark.key;

  show(mark: ChartMark): void {
    this.hovered = mark;
  }

  hide(): void {
    this.hovered = null;
  }

  private layout(): void {
    const categories = this.data.categories;
    const series = this.data.series;
    this.marks = [];
    this.ticks = [];
    this.categoryTicks = [];
    if (!categories.length || !series.length) {
      this.viewHeight = 120;
      return;
    }
    if (this.orientation === 'horizontal') {
      this.layoutHorizontal();
    } else {
      this.layoutVertical();
    }
  }

  /** Category names run down the left; bars grow to the right from one baseline. */
  private layoutHorizontal(): void {
    const { categories, series } = this.data;
    const stacked = this.mode === 'stacked';
    const rows = categories.length;
    const thickness = stacked
      ? BAR_MAX
      : Math.min(BAR_MAX, 22);
    const band = stacked ? BAR_MAX + 16 : series.length * (thickness + GAP) + 14;
    // On a narrow panel the reserved name column would leave no plot at all.
    const plotLeft = Math.min(this.categoryWidth, this.viewWidth * 0.42);
    const plotWidth = this.viewWidth - plotLeft - 54;
    const max = this.scaleMax();

    this.viewHeight = rows * band + 8;

    categories.forEach((category, row) => {
      const bandTop = row * band;
      this.categoryTicks.push({
        label: category,
        x: plotLeft - 12,
        y: bandTop + band / 2,
        anchor: 'end'
      });

      const total = this.categoryTotal(row);
      let cursor = plotLeft;
      series.forEach((entry, index) => {
        const value = entry.values[row] ?? 0;
        const basis = this.normalize && total > 0 ? value / total : value;
        const scale = this.normalize ? 1 : max;
        const full = scale > 0 ? (basis / scale) * plotWidth : 0;
        const width = Math.max(0, full - (stacked && index ? GAP : 0));
        const y = stacked
          ? bandTop + (band - thickness) / 2
          : bandTop + 7 + index * (thickness + GAP);
        const x = stacked ? cursor + (index ? GAP : 0) : plotLeft;
        const text = this.format(this.normalize ? basis : value);
        const fits = stacked && width > text.length * 7 + 14;

        this.marks.push({
          key: `${row}-${index}`,
          x, y, width, height: thickness,
          path: this.barPath(x, y, width, thickness, index === series.length - 1),
          slot: this.slotOf(index),
          category,
          series: entry.label,
          value,
          text,
          label: stacked ? (fits ? text : '') : text,
          labelX: stacked ? x + width / 2 : x + width + 8,
          labelY: y + thickness / 2,
          labelAnchor: stacked ? 'middle' : 'start',
          labelInside: stacked
        });
        cursor = x + width;
      });
    });
  }

  /** Categories run along the bottom; columns grow up from one baseline. */
  private layoutVertical(): void {
    const { categories, series } = this.data;
    const stacked = this.mode === 'stacked';
    const plotTop = 18;
    const plotHeight = 168;
    const baseline = plotTop + plotHeight;
    const plotLeft = AXIS_GUTTER + 12;
    /* A band wider than this leaves capped columns stranded in white, so the
       plot narrows instead of the marks spreading. */
    const available = this.viewWidth - plotLeft - 12;
    const plotWidth = Math.min(available, categories.length * BAND_MAX);
    const band = plotWidth / categories.length;
    const max = this.scaleMax();

    this.viewHeight = baseline + 34;

    // Four gridlines carry the values the marks are not labelled with.
    for (let step = 0; step <= 4; step++) {
      const value = (max / 4) * step;
      const y = baseline - (plotHeight / 4) * step;
      this.ticks.push({
        label: this.format(value),
        x: AXIS_GUTTER,
        y: y + 4,
        anchor: 'end',
        line: { x1: plotLeft, y1: y, x2: plotLeft + plotWidth, y2: y }
      });
    }

    categories.forEach((category, column) => {
      const bandLeft = plotLeft + column * band;
      this.categoryTicks.push({
        label: category,
        x: bandLeft + band / 2,
        y: baseline + 20,
        anchor: 'middle'
      });

      const total = this.categoryTotal(column);
      let cursor = baseline;
      series.forEach((entry, index) => {
        const value = entry.values[column] ?? 0;
        const basis = this.normalize && total > 0 ? value / total : value;
        const scale = this.normalize ? 1 : max;
        const full = scale > 0 ? (basis / scale) * plotHeight : 0;
        const thickness = stacked
          ? Math.min(BAR_MAX, band - 24)
          : Math.min(BAR_MAX, (band - 24) / series.length - GAP);
        const height = Math.max(0, full - (stacked && index ? GAP : 0));
        const x = stacked
          ? bandLeft + (band - thickness) / 2
          : bandLeft + (band - (thickness + GAP) * series.length) / 2
              + index * (thickness + GAP);
        const y = stacked ? cursor - height - (index ? GAP : 0) : baseline - height;
        const text = this.format(this.normalize ? basis : value);

        this.marks.push({
          key: `${column}-${index}`,
          x, y, width: thickness, height,
          path: this.barPath(x, y, thickness, height, index === series.length - 1),
          slot: this.slotOf(index),
          category,
          series: entry.label,
          value,
          text,
          label: stacked ? '' : text,
          labelX: x + thickness / 2,
          labelY: y - 7,
          labelAnchor: 'middle',
          labelInside: false
        });
        cursor = y;
      });
    });
  }

  /**
   * Bars are rounded at the data end and square at the baseline, so a stack
   * reads as one bar: only its outermost segment carries the corner.
   */
  private barPath(x: number, y: number, w: number, h: number, last: boolean): string {
    const square = `M${x},${y}h${w}v${h}h${-w}z`;
    const horizontal = this.orientation === 'horizontal';
    const along = horizontal ? w : h;
    const across = horizontal ? h : w;
    const r = Math.min(4, along, across / 2);
    if (!last || r <= 0 || along <= r) {
      return square;
    }
    return horizontal
      ? `M${x},${y}h${w - r}a${r},${r} 0 0 1 ${r},${r}v${h - 2 * r}`
        + `a${r},${r} 0 0 1 ${-r},${r}h${-(w - r)}z`
      : `M${x},${y + r}a${r},${r} 0 0 1 ${r},${-r}h${w - 2 * r}`
        + `a${r},${r} 0 0 1 ${r},${r}v${h - r}h${-w}z`;
  }

  /**
   * The palette only carries two non-alert steps — a third step of one hue
   * stops separating cleanly under colour-blindness simulation — so a series
   * alternates between them rather than fanning out; a fourth-plus series
   * repeats the cycle and leans on the legend and direct labels to disambiguate.
   */
  private slotOf(index: number): number | 'alert' {
    if (this.data.series[index]?.tone === 'alert') {
      return 'alert';
    }
    return index % 2 === 0 ? 1 : 3;
  }

  private categoryTotal(index: number): number {
    return this.data.series.reduce((sum, entry) => sum + (entry.values[index] ?? 0), 0);
  }

  /** Stacks scale to the largest total, grouped bars to the largest value. */
  private scaleMax(): number {
    if (this.normalize) {
      return 1;
    }
    const values = this.data.categories.map((_, index) =>
      this.mode === 'stacked'
        ? this.categoryTotal(index)
        : Math.max(...this.data.series.map(entry => entry.values[index] ?? 0)));
    return Math.max(...values, 1);
  }
}
