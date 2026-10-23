import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// Las acciones opcionales (p. ej. un botón para limpiar filtros) se proyectan como contenido.
@Component({
  selector: 'app-empty-state',
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly description = input<string | null>(null);
}
