import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges
} from '@angular/core';
import { ClassAnalysisResult } from '../../core/models/code-smell.model';

export interface ButterflyNode {
  id: string;
  name: string;
  simpleName: string;
  packageName: string;
  loc: number;
  riskScore: number;
  predictedLabel?: number;
  data?: ClassAnalysisResult;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ButterflyLink {
  id: string;
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  path: string;
  color: string;
  highlightColor: string;
  nodeId: string;
  isOutbound: boolean;
}

@Component({
  selector: 'ui-butterfly-graph',
  standalone: false,
  templateUrl: './ui-butterfly-graph.component.html'
})
export class UiButterflyGraphComponent implements OnChanges {
  @Input({ required: true }) focusClass!: ClassAnalysisResult;
  @Input() allClasses: ClassAnalysisResult[] = [];
  @Input() height = 480;

  @Output() selectClass = new EventEmitter<ClassAnalysisResult>();
  @Output() hoverClass = new EventEmitter<ClassAnalysisResult | null>();

  inboundNodes: ButterflyNode[] = [];
  outboundNodes: ButterflyNode[] = [];
  links: ButterflyLink[] = [];

  readonly viewWidth = 900;
  readonly viewHeight = 480;

  // Center node geometry
  readonly centerW = 230;
  readonly centerH = 138;
  centerPos = { x: 335, y: 171 };

  hoveredNodeId: string | null = null;
  hoveredNode: ButterflyNode | null = null;
  tooltipPos = { x: 0, y: 0 };

  extraInboundCount = 0;
  extraOutboundCount = 0;
  hiddenInboundClasses: ClassAnalysisResult[] = [];
  hiddenOutboundClasses: ClassAnalysisResult[] = [];
  showExtraDropdown: 'inbound' | 'outbound' | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['focusClass'] || changes['allClasses']) {
      this.computeGraph();
    }
  }

  computeGraph(): void {
    if (!this.focusClass) return;

    this.centerPos = {
      x: Math.round((this.viewWidth - this.centerW) / 2),
      y: Math.round((this.viewHeight - this.centerH) / 2) + 16
    };

    const otherClasses = this.allClasses.filter(c => c.className !== this.focusClass.className);
    const samePackage = otherClasses.filter(c => c.packageName === this.focusClass.packageName);
    const otherPackage = otherClasses.filter(c => c.packageName !== this.focusClass.packageName);

    const totalCa = Math.max(1, this.focusClass.ca || Math.round(this.focusClass.cbo * 0.45) || 2);
    const totalCe = Math.max(1, this.focusClass.ce || Math.round(this.focusClass.cbo * 0.55) || 2);

    const maxVisible = 5;
    const caCount = Math.min(maxVisible, totalCa);
    const ceCount = Math.min(maxVisible, totalCe);

    this.extraInboundCount = Math.max(0, totalCa - caCount);
    this.extraOutboundCount = Math.max(0, totalCe - ceCount);

    const inCandidates = [...samePackage, ...otherPackage];
    const outCandidates = [...otherPackage, ...samePackage];

    this.hiddenInboundClasses = inCandidates.slice(caCount);
    this.hiddenOutboundClasses = outCandidates.slice(ceCount);

    const nodeW = 172;
    const nodeH = 46;
    const headerOffsetY = 66;
    const availableHeight = this.viewHeight - headerOffsetY - 20;

    // Position Inbound (Left Column)
    const inPitch = availableHeight / Math.max(1, caCount);
    this.inboundNodes = inCandidates.slice(0, caCount).map((c, i) => {
      const nodeY = Math.round(headerOffsetY + i * inPitch + (inPitch - nodeH) / 2);
      return {
        ...this.toNode(c),
        x: 24,
        y: nodeY,
        width: nodeW,
        height: nodeH
      };
    });

    // Position Outbound (Right Column)
    const outPitch = availableHeight / Math.max(1, ceCount);
    this.outboundNodes = outCandidates.slice(0, ceCount).map((c, i) => {
      const nodeY = Math.round(headerOffsetY + i * outPitch + (outPitch - nodeH) / 2);
      return {
        ...this.toNode(c),
        x: this.viewWidth - nodeW - 24,
        y: nodeY,
        width: nodeW,
        height: nodeH
      };
    });

    // Construct high-precision Bezier links
    this.links = [];

    // Left -> Center
    this.inboundNodes.forEach((node, i) => {
      const startX = node.x + node.width;
      const startY = node.y + node.height / 2;
      const endX = this.centerPos.x;
      const endY = caCount <= 1
        ? this.centerPos.y + this.centerH / 2
        : this.centerPos.y + 24 + i * ((this.centerH - 48) / (caCount - 1));

      const dx = endX - startX;
      const c1x = startX + dx * 0.45;
      const c1y = startY;
      const c2x = endX - dx * 0.45;
      const c2y = endY;

      this.links.push({
        id: `in-${node.id}`,
        nodeId: node.id,
        isOutbound: false,
        fromX: startX,
        fromY: startY,
        toX: endX,
        toY: endY,
        path: `M ${startX} ${startY} C ${c1x} ${c1y}, ${c2x} ${c2y}, ${endX} ${endY}`,
        color: node.riskScore >= 0.6 ? 'rgba(239, 68, 68, 0.4)' : 'rgba(59, 130, 246, 0.4)',
        highlightColor: node.riskScore >= 0.6 ? '#ef4444' : '#3b82f6'
      });
    });

    // Center -> Right
    this.outboundNodes.forEach((node, i) => {
      const startX = this.centerPos.x + this.centerW;
      const startY = ceCount <= 1
        ? this.centerPos.y + this.centerH / 2
        : this.centerPos.y + 24 + i * ((this.centerH - 48) / (ceCount - 1));
      const endX = node.x;
      const endY = node.y + node.height / 2;

      const dx = endX - startX;
      const c1x = startX + dx * 0.45;
      const c1y = startY;
      const c2x = endX - dx * 0.45;
      const c2y = endY;

      this.links.push({
        id: `out-${node.id}`,
        nodeId: node.id,
        isOutbound: true,
        fromX: startX,
        fromY: startY,
        toX: endX,
        toY: endY,
        path: `M ${startX} ${startY} C ${c1x} ${c1y}, ${c2x} ${c2y}, ${endX} ${endY}`,
        color: node.riskScore >= 0.6 ? 'rgba(239, 68, 68, 0.4)' : 'rgba(16, 185, 129, 0.4)',
        highlightColor: node.riskScore >= 0.6 ? '#ef4444' : '#10b981'
      });
    });
  }

  private toNode(c: ClassAnalysisResult): Omit<ButterflyNode, 'x' | 'y' | 'width' | 'height'> {
    const parts = c.className.split('.');
    const simpleName = parts[parts.length - 1] || c.className;
    return {
      id: c.className,
      name: c.className,
      simpleName,
      packageName: c.packageName,
      loc: c.loc,
      riskScore: c.riskScore,
      predictedLabel: c.predictedLabel,
      data: c
    };
  }

  onNodeClick(node: ButterflyNode): void {
    if (node.data) {
      this.selectClass.emit(node.data);
    }
  }

  setHover(nodeId: string | null): void {
    this.hoveredNodeId = nodeId;
  }

  onNodeMouseEnter(node: ButterflyNode, event: MouseEvent): void {
    this.hoveredNodeId = node.id;
    this.hoveredNode = node;
    this.updateTooltipPos(event);
    this.hoverClass.emit(node.data || null);
  }

  onNodeMouseMove(event: MouseEvent): void {
    this.updateTooltipPos(event);
  }

  onNodeMouseLeave(): void {
    this.hoveredNodeId = null;
    this.hoveredNode = null;
    this.hoverClass.emit(null);
  }

  onExtraItemMouseEnter(c: ClassAnalysisResult): void {
    this.hoverClass.emit(c);
  }

  onExtraItemMouseLeave(): void {
    this.hoverClass.emit(null);
  }

  private updateTooltipPos(event: MouseEvent): void {
    const target = event.currentTarget as HTMLElement;
    const container = target.closest('.dl-butterfly-canvas-container') as HTMLElement;
    if (container) {
      const rect = container.getBoundingClientRect();
      const scrollLeft = container.scrollLeft || 0;
      const scrollTop = container.scrollTop || 0;
      const mouseX = event.clientX - rect.left + scrollLeft;
      const mouseY = event.clientY - rect.top + scrollTop;

      this.tooltipPos = {
        x: mouseX + 16,
        y: mouseY + 16
      };
    }
  }

  toggleExtraDropdown(type: 'inbound' | 'outbound'): void {
    this.showExtraDropdown = this.showExtraDropdown === type ? null : type;
  }

  selectExtraClass(c: ClassAnalysisResult): void {
    this.showExtraDropdown = null;
    this.selectClass.emit(c);
  }
}
