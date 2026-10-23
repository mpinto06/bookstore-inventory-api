import { TestBed } from '@angular/core/testing';

import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  const messages = () => service.toasts().map((toast) => toast.message);

  it('cierra cada toast a los 5 s y los errores a los 8 s', () => {
    service.success('ok');
    service.error('fallo');
    vi.advanceTimersByTime(4_999);
    expect(messages()).toEqual(['ok', 'fallo']);
    vi.advanceTimersByTime(1);
    expect(messages()).toEqual(['fallo']);
    vi.advanceTimersByTime(2_999);
    expect(messages()).toEqual(['fallo']);
    vi.advanceTimersByTime(1);
    expect(messages()).toEqual([]);
  });

  it('muestra como máximo 4 y descarta los más antiguos', () => {
    for (const n of [1, 2, 3, 4, 5]) {
      service.info(`mensaje ${n}`);
    }
    expect(messages()).toEqual(['mensaje 2', 'mensaje 3', 'mensaje 4', 'mensaje 5']);
  });

  it('no duplica un mensaje idéntico ya visible y reinicia su cuenta atrás', () => {
    service.info('igual');
    vi.advanceTimersByTime(4_000);
    service.info('igual');
    expect(service.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(4_000);
    expect(service.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(1_000);
    expect(service.toasts()).toHaveLength(0);
  });

  it('el mismo texto con otro tipo no cuenta como duplicado', () => {
    service.info('texto');
    service.error('texto');
    expect(service.toasts()).toHaveLength(2);
  });

  it('el cierre manual limpia el timer', () => {
    service.success('ok');
    service.dismiss(service.toasts()[0].id);
    expect(service.toasts()).toEqual([]);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('un toast descartado por el límite no deja su timer pendiente', () => {
    for (const n of [1, 2, 3, 4, 5]) {
      service.info(`mensaje ${n}`);
    }
    expect(vi.getTimerCount()).toBe(4);
  });
});
