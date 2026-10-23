import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// Las acciones opcionales (p. ej. un botón para limpiar filtros) se proyectan como contenido.
@Component({
  selector: 'app-empty-state',
  template: `
    <p class="empty-state__title" data-testid="empty-state-title">{{ title() }}</p>
    @if (description()) {
      <p class="empty-state__description">{{ description() }}</p>
    }
    <ng-content />
  `,
  styleUrl: './empty-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly description = input<string | null>(null);
}
