/** One column of a ui-table: how it's labelled, aligned, and whether it's frozen. */
export interface TableColumn {
  key: string;
  label: string;
  align?: 'left' | 'right';
  /** Freezes the column at this edge while the rest of the table scrolls horizontally. */
  sticky?: 'start' | 'end';
  /**
   * A CSS width hint (e.g. '28%'), so the table's 100% gets split deliberately
   * across columns instead of the browser guessing which one should stretch.
   * Content can still push a column wider than its hint if it has to.
   */
  width?: string;
  /** Extra class(es) applied to both the header and body cells, e.g. 'dl-mono'. */
  className?: string;
}
