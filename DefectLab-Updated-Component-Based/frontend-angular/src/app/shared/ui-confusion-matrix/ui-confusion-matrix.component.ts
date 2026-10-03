import { Component, Input } from '@angular/core';
import { ConfusionMatrix } from '../../core/models/defectlab.model';

/**
 * Reusable Confusion Matrix component displaying TP, FN, FP, TN, and row/column totals.
 */
@Component({
  selector: 'ui-confusion-matrix',
  standalone: false,
  templateUrl: './ui-confusion-matrix.component.html'
})
export class UiConfusionMatrixComponent {
  @Input() matrix: ConfusionMatrix | null = null;
}
