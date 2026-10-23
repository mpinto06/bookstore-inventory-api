import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'app-alert',
  template: `
    <div class="alert__body">
      <p class="alert__title" data-testid="alert-title">{{ title() }}</p>
      @if (message()) {
        <p class="alert__message" data-testid="alert-message">{{ message() }}</p>
      }
    </div>
    @if (showRetry()) {
      <button type="button" class="btn" data-testid="alert-retry" (click)="retry.emit()">
        Reintentar
      </button>
    }
  `,
  styleUrl: './alert.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'alert' },
})
export class AlertComponent {
  readonly title = input.required<string>();
  readonly message = input<string | null>(null);
  readonly showRetry = input(false);

  readonly retry = output<void>();
}
