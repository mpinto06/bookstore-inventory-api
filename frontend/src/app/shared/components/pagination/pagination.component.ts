import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

let nextId = 0;

@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  // Página actual 0-based (como la API); la vista muestra page + 1.
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly sizeOptions = input<readonly number[]>([10, 20, 50, 100]);
  readonly disabled = input(false);

  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  protected readonly sizeSelectId = `pagination-size-${nextId++}`;
  protected readonly displayPage = computed(() => this.page() + 1);
  protected readonly displayTotalPages = computed(() => Math.max(this.totalPages(), 1));
  protected readonly isFirst = computed(() => this.page() <= 0);
  protected readonly isLast = computed(() => this.page() >= this.totalPages() - 1);
  protected readonly resultsLabel = computed(() => {
    const total = this.totalElements();
    return `${total.toLocaleString('es')} ${total === 1 ? 'resultado' : 'resultados'}`;
  });

  protected previous(): void {
    if (!this.isFirst()) {
      this.pageChange.emit(this.page() - 1);
    }
  }

  protected next(): void {
    if (!this.isLast()) {
      this.pageChange.emit(this.page() + 1);
    }
  }

  protected onSizeChange(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (value !== this.size()) {
      this.sizeChange.emit(value);
    }
  }
}
