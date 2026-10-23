import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type StatusBadgeVariant = 'neutral' | 'warning' | 'danger' | 'success';

@Component({
  selector: 'app-status-badge',
  template: '<ng-content />',
  styleUrl: './status-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-variant]': 'variant()' },
})
export class StatusBadgeComponent {
  readonly variant = input<StatusBadgeVariant>('neutral');
}
