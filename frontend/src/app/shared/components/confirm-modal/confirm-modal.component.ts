import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  input,
  output,
  viewChild,
} from '@angular/core';

let nextId = 0;

// Modal de confirmación sobre <dialog> nativo (showModal da trampa de foco, capa superior y Escape).
// El padre lo renderiza con @if solo mientras hay algo que confirmar; el componente no inyecta servicios.
@Component({
  selector: 'app-confirm-modal',
  templateUrl: './confirm-modal.component.html',
  styleUrl: './confirm-modal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmModalComponent implements AfterViewInit, OnDestroy {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('Confirmar');
  // Petición en curso: el botón muestra el estado y no se puede cancelar ni cerrar.
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected readonly titleId = `confirm-modal-title-${nextId}`;
  protected readonly messageId = `confirm-modal-message-${nextId++}`;

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly cancelButton = viewChild.required<ElementRef<HTMLButtonElement>>('cancelButton');
  // El elemento que abrió el modal recupera el foco al cerrarse.
  private readonly opener =
    document.activeElement instanceof HTMLElement ? document.activeElement : null;

  ngAfterViewInit(): void {
    const dialog = this.dialog().nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.setAttribute('open', '');
    }
    this.cancelButton().nativeElement.focus();
  }

  ngOnDestroy(): void {
    if (this.opener?.isConnected) {
      this.opener.focus();
    }
  }

  // Escape dispara "cancel" en el <dialog>: el cierre lo decide el padre (destruyendo el componente).
  protected onNativeCancel(event: Event): void {
    event.preventDefault();
    this.requestCancel();
  }

  // En un <dialog> modal, un clic en el fondo (::backdrop) tiene como destino el propio <dialog>.
  protected onDialogClick(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) {
      this.requestCancel();
    }
  }

  protected requestCancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  protected confirm(): void {
    if (!this.busy()) {
      this.confirmed.emit();
    }
  }
}
