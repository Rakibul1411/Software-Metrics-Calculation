import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { ClassAnalysisResult } from '../../core/models/code-smell.model';
import { TreemapCell, TreemapGroup } from './ui-treemap.model';

@Component({
  selector: 'ui-treemap',
  standalone: false,
  templateUrl: './ui-treemap.component.html'
})
export class UiTreemapComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input({ required: true }) items: ClassAnalysisResult[] = [];
  @Input() height = 520;
  @Input() title = 'Codebase Hotspot Treemap';
  @Input() subtitle = 'Box size represents Lines of Code (LOC); color represents Defect Risk / Anti-Pattern Severity.';

  readonly Math = Math;

  @Output() selectClass = new EventEmitter<ClassAnalysisResult>();

  @ViewChild('container') private containerRef?: ElementRef<HTMLElement>;

  viewWidth = typeof window !== 'undefined' ? Math.max(800, window.innerWidth - 320) : 1200;
  viewHeight = 520;
  isReady = false;

  layoutMode: 'package' | 'flat' = 'package';
  packageGroups: TreemapGroup[] = [];

  cells: TreemapCell[] = [];
  selectedCell: TreemapCell | null = null;
  hoveredCell: TreemapCell | null = null;
  externalHoveredCell: TreemapCell | null = null;
  pulseCellId: string | null = null;
  tooltipPos = { x: 0, y: 0 };

  filter: 'all' | 'hotspots' | 'god_class' | 'coupling' = 'all';
  searchQuery = '';
  inspectorTab: 'metrics' | 'butterfly' = 'metrics';

  selectedPackage: string | null = null;

  get availablePackages(): Array<{ name: string; count: number; loc: number; hotspots: number }> {
    const map = new Map<string, { count: number; loc: number; hotspots: number }>();
    for (const item of this.items) {
      const pkg = item.packageName || '(root package)';
      const cur = map.get(pkg) || { count: 0, loc: 0, hotspots: 0 };
      cur.count++;
      cur.loc += item.loc;
      if (item.riskScore >= 0.5 || item.predictedLabel === 1 || item.smells.some(s => s.severity === 'CRITICAL')) {
        cur.hotspots++;
      }
      map.set(pkg, cur);
    }
    return Array.from(map.entries())
      .map(([name, data]) => ({ name, ...data }))
      .sort((a, b) => b.count - a.count || b.loc - a.loc);
  }

  selectPackage(pkgName: string | null): void {
    this.selectedPackage = pkgName;
    this.recompute();
  }

  setLayoutMode(mode: 'package' | 'flat'): void {
    this.layoutMode = mode;
    this.recompute();
  }

  setInspectorTab(tab: 'metrics' | 'butterfly'): void {
    this.inspectorTab = tab;
  }

  onButterflySelect(c: ClassAnalysisResult): void {
    // If the class belongs to a package outside current drilldown, reset drilldown so it's guaranteed visible
    if (this.selectedPackage && this.selectedPackage !== c.packageName) {
      this.selectedPackage = null;
      this.recompute();
    }

    let match = this.cells.find(cell => cell.id === c.className);
    if (!match) {
      // If search query or filter was hiding it, reset filter to 'all' and clear search
      if (this.filter !== 'all' || this.searchQuery) {
        this.filter = 'all';
        this.searchQuery = '';
        this.recompute();
        match = this.cells.find(cell => cell.id === c.className);
      }
    }

    if (match) {
      this.selectedCell = match;
      this.pulseCellId = match.id;
      this.scrollToCell(match);
      this.selectClass.emit(match.data);
      setTimeout(() => {
        if (this.pulseCellId === match!.id) {
          this.pulseCellId = null;
        }
      }, 3500);
    }
  }

  onExternalHoverClass(c: ClassAnalysisResult | null): void {
    if (!c) {
      this.externalHoveredCell = null;
      return;
    }
    const match = this.cells.find(cell => cell.id === c.className);
    this.externalHoveredCell = match || null;
  }

  scrollToCell(cell: TreemapCell): void {
    if (this.containerRef) {
      const container = this.containerRef.nativeElement;
      const targetX = Math.max(0, cell.x - (container.clientWidth / 2) + (cell.width / 2));
      const targetY = Math.max(0, cell.y - (container.clientHeight / 2) + (cell.height / 2));
      container.scrollTo({
        left: targetX,
        top: targetY,
        behavior: 'smooth'
      });
    }
  }

  private resizeObserver?: ResizeObserver;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['items'] || changes['height']) {
      this.viewHeight = this.height || 520;
      this.recompute();
    }
  }

  ngAfterViewInit(): void {
    if (this.containerRef) {
      const container = this.containerRef.nativeElement;
      const initialWidth = container.clientWidth || Math.round(container.getBoundingClientRect().width);
      if (initialWidth > 200) {
        this.viewWidth = initialWidth;
      }
      this.isReady = true;
      this.recompute();

      this.resizeObserver = new ResizeObserver(entries => {
        for (const entry of entries) {
          const cr = entry.contentRect;
          const newW = Math.floor(cr.width);
          if (newW > 200 && Math.abs(this.viewWidth - newW) > 4) {
            this.viewWidth = newW;
            this.recompute();
          }
        }
      });
      this.resizeObserver.observe(container);
    } else {
      this.isReady = true;
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  get filteredItems(): ClassAnalysisResult[] {
    let result = this.items;
    if (this.selectedPackage) {
      result = result.filter(i => (i.packageName || '(root package)') === this.selectedPackage);
    }
    if (this.filter === 'hotspots') {
      result = result.filter(i => i.riskScore >= 0.5 || i.predictedLabel === 1 || i.smells.some(s => s.severity === 'CRITICAL'));
    } else if (this.filter === 'god_class') {
      result = result.filter(i => i.smells.some(s => s.type === 'GOD_CLASS'));
    } else if (this.filter === 'coupling') {
      result = result.filter(i => i.smells.some(s => s.type === 'SPAGHETTI_COUPLING'));
    }
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      result = result.filter(i => i.className.toLowerCase().includes(q));
    }
    return result;
  }

  get hotspotCount(): number {
    return this.items.filter(i => i.riskScore >= 0.5 || i.predictedLabel === 1 || i.smells.some(s => s.severity === 'CRITICAL')).length;
  }

  get godClassCount(): number {
    return this.items.filter(i => i.smells.some(s => s.type === 'GOD_CLASS')).length;
  }

  get couplingRiskCount(): number {
    return this.items.filter(i => i.smells.some(s => s.type === 'SPAGHETTI_COUPLING')).length;
  }

  setFilter(f: 'all' | 'hotspots' | 'god_class' | 'coupling'): void {
    this.filter = f;
    this.recompute();
  }

  onSearchChange(q: string): void {
    this.searchQuery = q;
    this.recompute();
  }

  recompute(): void {
    const list = this.filteredItems;
    if (!list || list.length === 0) {
      this.cells = [];
      this.packageGroups = [];
      return;
    }

    const w = Math.max(300, this.viewWidth);
    const h = Math.max(300, this.viewHeight);

    if (this.layoutMode === 'package' && !this.selectedPackage) {
      this.computePackageLayout(list, w, h);
    } else {
      // Flat or drilled into a single package
      this.packageGroups = [];
      this.cells = this.squarifyLayout(list, 0, 0, w, h, 2);
    }

    // Keep selected cell reference up to date if still visible
    if (this.selectedCell) {
      const match = this.cells.find(c => c.id === this.selectedCell?.id);
      this.selectedCell = match || null;
    }
  }

  private computePackageLayout(items: ClassAnalysisResult[], w: number, h: number): void {
    const pkgMap = new Map<string, ClassAnalysisResult[]>();
    for (const item of items) {
      const pkg = item.packageName || '(root package)';
      const arr = pkgMap.get(pkg) || [];
      arr.push(item);
      pkgMap.set(pkg, arr);
    }

    interface PkgData {
      name: string;
      classes: ClassAnalysisResult[];
      weight: number;
      hotspotCount: number;
    }

    const pkgList: PkgData[] = [];
    for (const [pkgName, classes] of pkgMap.entries()) {
      const weight = classes.reduce((sum, c) => sum + Math.max(10, c.loc), 0);
      const hotspotCount = classes.filter(c => c.riskScore >= 0.5 || c.predictedLabel === 1 || c.smells.some(s => s.severity === 'CRITICAL')).length;
      pkgList.push({ name: pkgName, classes, weight, hotspotCount });
    }

    // Sort packages by total weight descending
    pkgList.sort((a, b) => b.weight - a.weight);

    // Apply minimum floor weight for layout so small packages remain readable and clickable
    const rawTotal = pkgList.reduce((sum, p) => sum + p.weight, 0);
    const minFloor = Math.max(100, rawTotal * 0.012);
    const pkgItemsForSquarify = pkgList.map(p => ({
      ...p,
      weight: Math.max(minFloor, p.weight)
    }));

    const pkgRects = this.squarify(pkgItemsForSquarify, 0, 0, w, h);

    const allCells: TreemapCell[] = [];
    this.packageGroups = pkgList.map((pkg, idx) => {
      const rect = pkgRects[idx] || { x: 0, y: 0, width: w, height: h };
      const headerH = 22;
      const innerX = rect.x + 2;
      const innerY = rect.y + headerH + 1;
      const innerW = Math.max(6, rect.width - 4);
      const innerH = Math.max(6, rect.height - headerH - 3);

      const pkgCells = this.squarifyLayout(pkg.classes, innerX, innerY, innerW, innerH, 2);
      allCells.push(...pkgCells);

      return {
        packageName: pkg.name,
        cells: pkgCells,
        x: rect.x,
        y: rect.y,
        width: rect.width,
        height: rect.height,
        totalSize: pkg.weight,
        hotspotCount: pkg.hotspotCount
      };
    });

    this.cells = allCells;
  }

  private squarify<T extends { weight: number }>(
    items: T[],
    x: number,
    y: number,
    w: number,
    h: number
  ): Array<{ item: T; x: number; y: number; width: number; height: number }> {
    if (items.length === 0) return [];
    if (items.length === 1) {
      return [{ item: items[0], x, y, width: w, height: h }];
    }

    const totalWeight = items.reduce((sum, it) => sum + it.weight, 0);
    if (totalWeight <= 0 || w <= 0 || h <= 0) {
      return items.map(item => ({ item, x, y, width: w, height: h }));
    }

    const normalized = items.map((item, idx) => ({
      item,
      idx,
      area: (item.weight / totalWeight) * (w * h)
    }));

    const rects: Array<{ item: T; x: number; y: number; width: number; height: number }> = new Array(items.length);
    let rw = w;
    let rh = h;
    let rx = x;
    let ry = y;
    let row: typeof normalized = [];

    const worstRatio = (rowItems: typeof normalized, side: number): number => {
      const s = rowItems.reduce((sum, it) => sum + it.area, 0);
      if (s === 0 || side === 0) return Infinity;
      const thickness = s / side;
      let worst = 0;
      for (const it of rowItems) {
        const length = it.area / thickness;
        const ratio = length === 0 ? Infinity : Math.max(thickness / length, length / thickness);
        if (ratio > worst) worst = ratio;
      }
      return worst;
    };

    const layoutRow = (rowItems: typeof normalized, side: number): void => {
      const s = rowItems.reduce((sum, it) => sum + it.area, 0);
      const thickness = side > 0 ? s / side : (rw <= rh ? rh : rw);
      if (rw <= rh) {
        let curX = rx;
        for (const it of rowItems) {
          const itemW = thickness > 0 ? it.area / thickness : rw;
          rects[it.idx] = { item: it.item, x: curX, y: ry, width: itemW, height: thickness };
          curX += itemW;
        }
        ry += thickness;
        rh -= thickness;
      } else {
        let curY = ry;
        for (const it of rowItems) {
          const itemH = thickness > 0 ? it.area / thickness : rh;
          rects[it.idx] = { item: it.item, x: rx, y: curY, width: thickness, height: itemH };
          curY += itemH;
        }
        rx += thickness;
        rw -= thickness;
      }
    };

    for (const it of normalized) {
      const side = Math.min(rw, rh);
      if (row.length === 0) {
        row = [it];
      } else {
        const withIt = [...row, it];
        if (worstRatio(withIt, side) <= worstRatio(row, side)) {
          row.push(it);
        } else {
          layoutRow(row, side);
          row = [it];
        }
      }
    }

    if (row.length > 0) {
      layoutRow(row, Math.min(rw, rh));
    }

    return rects;
  }

  private squarifyLayout(
    items: ClassAnalysisResult[],
    x: number,
    y: number,
    width: number,
    height: number,
    gap: number
  ): TreemapCell[] {
    if (items.length === 0) return [];
    const sorted = [...items].sort((a, b) => Math.max(5, b.loc) - Math.max(5, a.loc));
    const layoutItems = sorted.map(c => ({
      classItem: c,
      weight: Math.max(5, c.loc)
    }));

    const rects = this.squarify(layoutItems, x, y, width, height);
    return rects.map(r => {
      return this.createCell(
        r.item.classItem,
        r.x + gap / 2,
        r.y + gap / 2,
        Math.max(1, r.width - gap),
        Math.max(1, r.height - gap)
      );
    });
  }

  private createCell(
    data: ClassAnalysisResult,
    x: number,
    y: number,
    w: number,
    h: number
  ): TreemapCell {
    const parts = data.className.split('.');
    const simpleName = parts[parts.length - 1] || data.className;

    let color: string;
    const hasCriticalSmell = data.smells.some(s => s.severity === 'CRITICAL');
    const isBuggy = data.predictedLabel === 1 || data.riskScore >= 0.5;

    if (hasCriticalSmell || data.riskScore >= 0.7) {
      color = '#be123c'; // SciTools Understand Crimson Hotspot
    } else if (isBuggy || data.riskScore >= 0.4 || data.smells.length > 0) {
      color = '#b45309'; // Warning Amber
    } else {
      color = '#047857'; // Deep Clean Emerald
    }

    return {
      id: data.className,
      name: data.className,
      simpleName,
      packageName: data.packageName,
      size: data.loc,
      riskScore: data.riskScore,
      predictedLabel: data.predictedLabel,
      smells: data.smells,
      x: Math.max(0, Math.round(x)),
      y: Math.max(0, Math.round(y)),
      width: Math.max(2, Math.round(w)),
      height: Math.max(2, Math.round(h)),
      color,
      data
    };
  }

  onCellClick(cell: TreemapCell): void {
    this.selectedCell = cell;
    this.selectClass.emit(cell.data);
  }

  onCellMouseEnter(cell: TreemapCell, event: MouseEvent): void {
    this.hoveredCell = cell;
    this.updateTooltipPos(event);
  }

  onCellMouseMove(event: MouseEvent): void {
    this.updateTooltipPos(event);
  }

  onCellMouseLeave(): void {
    this.hoveredCell = null;
  }

  private updateTooltipPos(event: MouseEvent): void {
    if (this.containerRef) {
      const container = this.containerRef.nativeElement;
      const rect = container.getBoundingClientRect();
      const scrollLeft = container.scrollLeft || 0;
      const scrollTop = container.scrollTop || 0;

      const mouseX = event.clientX - rect.left + scrollLeft;
      const mouseY = event.clientY - rect.top + scrollTop;

      const tooltipWidth = 270;
      const tooltipHeight = 160;

      let x = mouseX + 16;
      let y = mouseY + 16;

      // Flip left if overflowing visible container width
      if (event.clientX - rect.left + tooltipWidth > rect.width - 20) {
        x = mouseX - tooltipWidth - 14;
      }

      // Flip up if overflowing visible container height
      if (event.clientY - rect.top + tooltipHeight > rect.height - 20) {
        y = mouseY - tooltipHeight - 14;
      }

      this.tooltipPos = {
        x: Math.max(scrollLeft + 8, x),
        y: Math.max(scrollTop + 8, y)
      };
    }
  }

  closeInspector(): void {
    this.selectedCell = null;
  }
}
