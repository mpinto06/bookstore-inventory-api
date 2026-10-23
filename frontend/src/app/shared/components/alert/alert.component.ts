import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'app-alert',
  templateUrl: './alert.component.html',
  styleUrl: './alert.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.role]': "variant() === 'error' ? 'alert' : 'status'",
    '[attr.data-variant]': 'variant()',
  },
})
export class AlertComponent {
  readonly title = input.required<string>();
  readonly message = input<string | null>(null);
  readonly showRetry = input(false);
  readonly variant = input<'error' | 'warning'>('error');

  readonly retry = output<void>();
}
