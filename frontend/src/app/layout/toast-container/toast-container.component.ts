import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { ToastService } from '../../core/ui/toast.service';

// Se monta una sola vez en main-layout. Dos regiones vivas persistentes: los avisos informativos se
// anuncian con cortesía (status/polite) y los errores de inmediato (alert).
@Component({
  selector: 'app-toast-container',
  templateUrl: './toast-container.component.html',
  styleUrl: './toast-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastContainerComponent {
  private readonly toastService = inject(ToastService);

  protected readonly polite = computed(() =>
    this.toastService.toasts().filter((toast) => toast.kind !== 'error'),
  );
  protected readonly errors = computed(() =>
    this.toastService.toasts().filter((toast) => toast.kind === 'error'),
  );

  protected dismiss(id: number): void {
    this.toastService.dismiss(id);
  }
}
