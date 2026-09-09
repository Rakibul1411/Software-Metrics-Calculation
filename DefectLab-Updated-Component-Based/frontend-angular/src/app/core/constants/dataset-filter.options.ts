import { SelectOption } from '../../shared/ui-select/ui-select.model';
import { RadioOption } from '../../shared/ui-radio-group/ui-radio-group.model';

/** Metric-family dropdown shared by the dataset and comparison screens. */
export const FAMILY_FILTER_OPTIONS: SelectOption[] = [
  { value: '', label: 'All families' },
  { value: 'PROMISE', label: 'PROMISE' },
  { value: 'AEEEM', label: 'AEEEM' }
];

/** Dataset-origin dropdown. */
export const ORIGIN_FILTER_OPTIONS: SelectOption[] = [
  { value: '', label: 'All origins' },
  { value: 'PREDEFINED', label: 'Predefined dataset' },
  { value: 'MANUAL', label: 'Manually extracted' }
];

/** Family radio buttons shown on the extraction and prediction forms. */
export const FAMILY_RADIO_OPTIONS: RadioOption[] = [
  { value: 'PROMISE', label: 'PROMISE · 20 metrics' },
  { value: 'AEEEM', label: 'AEEEM · 56 metrics' }
];
