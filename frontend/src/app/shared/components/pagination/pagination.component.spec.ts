import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaginationComponent } from './pagination.component';

interface PaginationInputs {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

describe('PaginationComponent', () => {
  let fixture: ComponentFixture<PaginationComponent>;

  function render(inputs: PaginationInputs): void {
    fixture = TestBed.createComponent(PaginationComponent);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
  }

  function byTestId<T extends HTMLElement>(id: string): T {
    const element = fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
    if (!element) {
      throw new Error(`No existe [data-testid="${id}"]`);
    }
    return element as T;
  }

  it('muestra "Página X de Y" con la página 1-based y el total de resultados', () => {
    render({ page: 1, size: 20, totalElements: 45, totalPages: 3 });

    expect(byTestId('pagination-indicator').textContent?.trim()).toBe('Página 2 de 3');
    expect(byTestId('pagination-indicator').getAttribute('aria-current')).toBe('page');
    expect(byTestId('pagination-total').textContent?.trim()).toBe('45 resultados');
  });

  it('deshabilita "Anterior" en la primera página', () => {
    render({ page: 0, size: 20, totalElements: 45, totalPages: 3 });

    expect(byTestId<HTMLButtonElement>('pagination-previous').disabled).toBe(true);
    expect(byTestId<HTMLButtonElement>('pagination-next').disabled).toBe(false);
  });

  it('deshabilita "Siguiente" en la última página', () => {
    render({ page: 2, size: 20, totalElements: 45, totalPages: 3 });

    expect(byTestId<HTMLButtonElement>('pagination-previous').disabled).toBe(false);
    expect(byTestId<HTMLButtonElement>('pagination-next').disabled).toBe(true);
  });

  it('sin resultados muestra "Página 1 de 1" con ambos extremos deshabilitados', () => {
    render({ page: 0, size: 20, totalElements: 0, totalPages: 0 });

    expect(byTestId('pagination-indicator').textContent?.trim()).toBe('Página 1 de 1');
    expect(byTestId<HTMLButtonElement>('pagination-previous').disabled).toBe(true);
    expect(byTestId<HTMLButtonElement>('pagination-next').disabled).toBe(true);
  });

  it('emite pageChange con la página siguiente y la anterior (0-based)', () => {
    render({ page: 1, size: 20, totalElements: 45, totalPages: 3 });
    const emitted: number[] = [];
    fixture.componentInstance.pageChange.subscribe((page) => emitted.push(page));

    byTestId('pagination-next').click();
    byTestId('pagination-previous').click();
    fixture.detectChanges();

    expect(emitted).toEqual([2, 0]);
  });

  it('emite sizeChange con el tamaño elegido', () => {
    render({ page: 0, size: 20, totalElements: 45, totalPages: 3 });
    const emitted: number[] = [];
    fixture.componentInstance.sizeChange.subscribe((size) => emitted.push(size));

    const select = byTestId<HTMLSelectElement>('pagination-size');
    expect(select.value).toBe('20');
    select.value = '50';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(emitted).toEqual([50]);
  });

  it('deshabilita todos los controles mientras está deshabilitado', () => {
    render({ page: 1, size: 20, totalElements: 45, totalPages: 3 });
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    expect(byTestId<HTMLButtonElement>('pagination-previous').disabled).toBe(true);
    expect(byTestId<HTMLButtonElement>('pagination-next').disabled).toBe(true);
    expect(byTestId<HTMLSelectElement>('pagination-size').disabled).toBe(true);
  });
});
