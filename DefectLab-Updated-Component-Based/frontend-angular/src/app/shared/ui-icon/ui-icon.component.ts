import { Component, Input } from '@angular/core';

/**
 * The application's icon set.
 *
 * Every glyph is a stroked 24x24 path so they share one optical weight and
 * inherit colour from their container. Adding an icon means adding one entry
 * here — no page ever inlines raw SVG.
 */
export type UiIconName =
  | 'check'
  | 'view'
  | 'download'
  | 'trash'
  | 'refresh'
  | 'plus'
  | 'close'
  | 'arrow-right'
  | 'arrow-left'
  | 'search'
  | 'upload'
  | 'warning'
  | 'chart'
  | 'sun'
  | 'moon'
  | 'user'
  | 'logout'
  | 'view-off'
  | 'undo'
  | 'chevron-right'
  | 'chevron-left'
  | 'chevron-down'
  | 'chevron-up'
  | 'expand-more'
  | 'expand-less'
  | 'chevrons-left'
  | 'chevrons-right'
  | 'info'
  | 'clock'
  | 'shield'
  | 'target'
  | 'database'
  | 'code'
  | 'folder'
  | 'scatter';

const PATHS: Record<UiIconName, string> = {
  'scatter': 'M7.5 7.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm9 5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm-7 7a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm10 2a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM4 15a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z',
  'check': 'M4 12.5 9 17.5 20 6.5',
  'view': 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  'download': 'M12 3v12m0 0 4-4m-4 4-4-4M4 20h16',
  'trash': 'M4 7h16M9 7V5h6v2m-8 0 1 13h8l1-13M10 11v6m4-6v6',
  'refresh': 'M20 12a8 8 0 1 1-2.6-5.9M20 4v5h-5',
  'plus': 'M12 5v14M5 12h14',
  'close': 'M6 6l12 12M18 6 6 18',
  'arrow-right': 'M5 12h13m0 0-5-5m5 5-5 5',
  'arrow-left': 'M19 12H6m0 0 5 5m-5-5 5-5',
  'search': 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14Zm5.5-1.5L21 21',
  'upload': 'M12 21V9m0 0 4 4m-4-4-4 4M4 4h16',
  'warning': 'M12 4 2.5 20h19L12 4Zm0 6v5m0 3v.5',
  'chart': 'M4 20V10m5 10V4m5 16v-7m5 7V8',
  'sun': 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0-14v2m0 14v2M3 12h2m14 0h2M5.6 5.6l1.4 1.4m10 10 1.4 1.4m0-12.8-1.4 1.4m-10 10-1.4 1.4',
  'moon': 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z',
  'user': 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 8a8 8 0 0 1 16 0',
  'logout': 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 8 6 12l4 4m-4-4h11',
  'view-off': 'M3 3l18 18M10.6 10.6a3 3 0 0 0 4.2 4.2M9.9 5.2A9.5 9.5 0 0 1 12 5c6 0 9.5 6 9.5 6a15 15 0 0 1-3 3.6M6.5 6.6A15 15 0 0 0 2.5 11S6 17 12 17a9 9 0 0 0 3.2-.6',
  'undo': 'M9 15 3 9m0 0 6-6M3 9h12a6 6 0 0 1 0 12',
  'chevron-right': 'M9 5l7 7-7 7',
  'chevron-left': 'M15 19l-7-7 7-7',
  'chevron-down': 'M6 9l6 6 6-6',
  'chevron-up': 'M18 15l-6-6-6 6',
  'expand-more': 'M6 9l6 6 6-6',
  'expand-less': 'M18 15l-6-6-6 6',
  'chevrons-left': 'M11 19l-7-7 7-7m8 14l-7-7 7-7',
  'chevrons-right': 'M13 5l7 7-7 7M5 5l7 7-7 7',
  'info': 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-8v5m0-9v.01',
  'clock': 'M12 6v6l4 2m6-2a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  'shield': 'M12 3s7 3 7 8c0 5-7 10-7 10S5 16 5 11c0-5 7-8 7-8Z',
  'target': 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Zm0-6a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
  'database': 'M12 4c4.4 0 8 1.3 8 3v10c0 1.7-3.6 3-8 3s-8-1.3-8-3V7c0-1.7 3.6-3 8-3Zm0 6c4.4 0 8-1.3 8-3M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3',
  'code': 'M8 9l-4 3 4 3m8-6l4 3-4 3m-6 3l2-12',
  'folder': 'M3 7v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-6l-2-2H5a2 2 0 0 0-2 2Z'
};

@Component({
  selector: 'ui-icon',
  standalone: false,
  templateUrl: './ui-icon.component.html'
})
export class UiIconComponent {
  @Input({ required: true }) name!: UiIconName;
  @Input() size = 16;

  get path(): string {
    return PATHS[this.name] ?? '';
  }
}
