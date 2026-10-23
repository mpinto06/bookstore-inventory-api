import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { AlertComponent } from '../../../../shared/components/alert/alert.component';
import { PriceCalculation } from '../../models/book.model';

// Formatea un importe con el código de moneda; si el navegador no lo soporta, número + código (nunca lanza).
export function formatMoney(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat('es', { style: 'currency', currency }).format(value);
  } catch {
    return `${formatNumber(value, 2, 2)} ${currency}`;
  }
}

// Sin recortar decimales significativos (tasas como 0,8512345).
export function formatRate(value: number): string {
  return formatNumber(value, 2, 12);
}

export function formatNumber(value: number, minDecimals: number, maxDecimals: number): string {
  return new Intl.NumberFormat('es', {
    minimumFractionDigits: minDecimals,
    maximumFractionDigits: maxDecimals,
  }).format(value);
}

// Desglose del último cálculo de precio. Componente tonto: solo recibe el resultado.
@Component({
  selector: 'app-price-breakdown',
  imports: [DatePipe, AlertComponent],
  templateUrl: './price-breakdown.component.html',
  styleUrl: './price-breakdown.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceBreakdownComponent {
  readonly result = input.required<PriceCalculation>();

  protected readonly costUsd = computed(() => formatMoney(this.result().cost_usd, 'USD'));
  protected readonly rate = computed(
    () => `1 USD = ${formatRate(this.result().exchange_rate)} ${this.result().currency}`,
  );
  protected readonly costLocal = computed(() =>
    formatMoney(this.result().cost_local, this.result().currency),
  );
  protected readonly margin = computed(
    () => `${formatNumber(this.result().margin_percentage, 0, 2)} %`,
  );
  protected readonly sellingPrice = computed(() =>
    formatMoney(this.result().selling_price_local, this.result().currency),
  );
  protected readonly isFallback = computed(() => this.result().rate_source === 'FALLBACK');
  protected readonly rateDateLabel = computed(() => formatIsoDate(this.result().rate_date));
}

// "2026-10-23" -> "23/10/2026" sin pasar por Date (evita desfases de zona horaria).
function formatIsoDate(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value;
}
