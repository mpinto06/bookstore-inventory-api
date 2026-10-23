import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type StatusBadgeVariant = 'neutral' | 'warning' | 'danger' | 'success';

@Component({
  selector: 'app-status-badge',
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-variant]': 'variant()' },
})
export class StatusBadgeComponent {
  readonly variant = input<StatusBadgeVariant>('neutral');
}
