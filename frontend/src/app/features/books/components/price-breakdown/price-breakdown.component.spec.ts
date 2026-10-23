import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PriceCalculation } from '../../models/book.model';
import { PriceBreakdownComponent, formatMoney, formatRate } from './price-breakdown.component';

const LIVE: PriceCalculation = {
  book_id: 1,
  cost_usd: 15.99,
  exchange_rate: 0.892,
  cost_local: 14.26,
  margin_percentage: 40,
  selling_price_local: 19.96,
  currency: 'EUR',
  calculation_timestamp: '2026-10-23T03:20:48.824103400Z',
  rate_source: 'LIVE',
  rate_date: '2026-10-23',
};

describe('PriceBreakdownComponent', () => {
  let fixture: ComponentFixture<PriceBreakdownComponent>;

  function render(result: PriceCalculation): HTMLElement {
    fixture = TestBed.createComponent(PriceBreakdownComponent);
    fixture.componentRef.setInput('result', result);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (root: HTMLElement, id: string) =>
    root.querySelector(`[data-testid="${id}"]`)?.textContent?.replace(/\s+/g, ' ').trim();

  it('muestra el desglose coherente con la respuesta y sin aviso con tasa LIVE', () => {
    const root = render(LIVE);
    expect(text(root, 'bd-rate')).toBe('1 USD = 0,892 EUR');
    expect(text(root, 'bd-margin')).toBe('40 %');
    expect(text(root, 'bd-rate-date')).toBe('23/10/2026');
    expect(text(root, 'bd-cost-local')).toContain('14,26');
    expect(text(root, 'bd-selling-price')).toContain('19,96');
    expect(root.querySelector('[data-testid="fallback-warning"]')).toBeNull();
  });

  it('con tasa FALLBACK muestra el aviso con la fecha de la tasa', () => {
    const root = render({ ...LIVE, rate_source: 'FALLBACK', rate_date: '2026-10-20' });
    const warning = root.querySelector('[data-testid="fallback-warning"]');
    expect(warning?.textContent).toContain(
      'Se usó una tasa de respaldo del 20/10/2026; puede no estar actualizada',
    );
    expect(warning?.getAttribute('role')).toBe('status');
  });

  it('una moneda que el navegador no soporta no rompe la vista', () => {
    const root = render({ ...LIVE, currency: 'ABCD' });
    expect(text(root, 'bd-selling-price')).toBe('19,96 ABCD');
  });
});

describe('formatMoney / formatRate', () => {
  it('cae a número + código cuando Intl lanza', () => {
    expect(formatMoney(1234.5, 'ABCD')).toBe('1234,50 ABCD');
  });

  it('no recorta decimales significativos de la tasa', () => {
    expect(formatRate(886.23)).toBe('886,23');
    expect(formatRate(0.8512345678)).toBe('0,8512345678');
    expect(formatRate(4)).toBe('4,00');
  });
});
