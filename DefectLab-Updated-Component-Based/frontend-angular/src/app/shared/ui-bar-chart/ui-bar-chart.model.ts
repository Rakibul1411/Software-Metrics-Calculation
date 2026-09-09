/** One measured series across every category of a chart. */
export interface ChartSeries {
  label: string;
  /** One value per category, in the same order as {@link ChartData.categories}. */
  values: number[];
  /**
   * 'alert' paints this series in the warm tone reserved for a finding —
   * predicted-buggy — instead of a step of the neutral ramp.
   */
  tone?: 'alert';
}

export interface ChartData {
  categories: string[];
  series: ChartSeries[];
}

/** How a value is written on a mark and in its tooltip. */
export type ChartValueFormat = 'integer' | 'decimal' | 'percent';

/** A single painted rectangle, positioned in SVG user units. */
export interface ChartMark {
  key: string;
  x: number;
  y: number;
  width: number;
  height: number;
  /** Outline with a rounded data-end and a square baseline end. */
  path: string;
  /** Fill key: a 1-based ramp step, or 'alert' for the reserved warm tone. */
  slot: number | 'alert';
  category: string;
  series: string;
  value: number;
  text: string;
  /** Value drawn beside the mark; empty when it would not fit. */
  label: string;
  labelX: number;
  labelY: number;
  labelAnchor: 'start' | 'middle' | 'end';
  /** Set when the label sits on the fill rather than the surface. */
  labelInside: boolean;
}

export interface ChartTick {
  label: string;
  x: number;
  y: number;
  /** Gridline endpoints; absent on the category axis. */
  line?: { x1: number; y1: number; x2: number; y2: number };
  anchor: 'start' | 'middle' | 'end';
}
