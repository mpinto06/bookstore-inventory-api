import { DestroyRef, Injectable, inject, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

export const MAX_VISIBLE_TOASTS = 4;
export const TOAST_DURATION_MS = 5_000;
export const TOAST_ERROR_DURATION_MS = 8_000;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly state = signal<readonly Toast[]>([]);
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  private nextId = 1;

  readonly toasts = this.state.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.timers.forEach((timer) => clearTimeout(timer)));
  }

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('error', message);
  }

  warning(message: string): void {
    this.show('warning', message);
  }

  info(message: string): void {
    this.show('info', message);
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this.state.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  private show(kind: ToastKind, message: string): void {
    const duplicate = this.state().find(
      (toast) => toast.kind === kind && toast.message === message,
    );
    if (duplicate) {
      // Ya está visible: no se duplica, solo se reinicia su cuenta atrás.
      this.schedule(duplicate);
      return;
    }
    const toast: Toast = { id: this.nextId++, kind, message };
    const overflow = this.state().length + 1 - MAX_VISIBLE_TOASTS;
    if (overflow > 0) {
      this.state()
        .slice(0, overflow)
        .forEach((oldest) => this.clearTimer(oldest.id));
    }
    this.state.update((toasts) => [...toasts, toast].slice(-MAX_VISIBLE_TOASTS));
    this.schedule(toast);
  }

  private schedule(toast: Toast): void {
    this.clearTimer(toast.id);
    const duration = toast.kind === 'error' ? TOAST_ERROR_DURATION_MS : TOAST_DURATION_MS;
    this.timers.set(
      toast.id,
      setTimeout(() => this.dismiss(toast.id), duration),
    );
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
  }
}
