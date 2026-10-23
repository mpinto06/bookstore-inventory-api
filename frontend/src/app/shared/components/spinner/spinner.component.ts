import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  template: `
    <span class="spinner__circle" aria-hidden="true"></span>
    <span class="visually-hidden">{{ label() }}</span>
  `,
  styleUrl: './spinner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'status', '[attr.data-size]': 'size()' },
})
export class SpinnerComponent {
  readonly label = input('Cargando…');
  readonly size = input<'sm' | 'md' | 'lg'>('md');
}
