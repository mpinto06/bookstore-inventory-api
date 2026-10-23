import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiError } from '../../../../core/http/api-error.model';
import { apiErrorMessage } from '../../../../core/http/api-error-message';
import { ToastService } from '../../../../core/ui/toast.service';
import { AlertComponent } from '../../../../shared/components/alert/alert.component';
import { ConfirmModalComponent } from '../../../../shared/components/confirm-modal/confirm-modal.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { CountryNamePipe } from '../../../../shared/pipes/country-name.pipe';
import { Book, PriceCalculation } from '../../models/book.model';
import { BOOKS_ROOT, parseBookId, readBackUrl } from '../../navigation/book-navigation';
import { BookService } from '../../services/book.service';
import { PriceBreakdownComponent, formatMoney } from '../price-breakdown/price-breakdown.component';

type LoadState = 'loading' | 'ready' | 'not-found' | 'error';

@Component({
  selector: 'app-book-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    AlertComponent,
    ConfirmModalComponent,
    CountryNamePipe,
    EmptyStateComponent,
    PriceBreakdownComponent,
    SpinnerComponent,
  ],
  templateUrl: './book-detail.component.html',
  styleUrl: './book-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookService = inject(BookService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly bookId = parseBookId(this.route.snapshot.paramMap.get('id'));
  protected readonly backUrl = readBackUrl(this.router);
  // UrlTree: un string en routerLink codificaría el "?" de los filtros como parte del path.
  protected readonly backTree = this.router.parseUrl(this.backUrl);
  // Tras borrar no se puede volver al detalle: se usa el listado (con sus filtros si se vino de él).
  private readonly afterDeleteUrl = this.backUrl.startsWith(`${BOOKS_ROOT}/`)
    ? BOOKS_ROOT
    : this.backUrl;
  protected readonly detailUrl = `${BOOKS_ROOT}/${this.bookId}`;

  protected readonly loadState = signal<LoadState>('loading');
  protected readonly loadError = signal<ApiError | null>(null);
  protected readonly book = signal<Book | null>(null);

  protected readonly calculating = signal(false);
  protected readonly priceResult = signal<PriceCalculation | null>(null);

  protected readonly confirmingDelete = signal(false);
  protected readonly deleting = signal(false);

  protected readonly costLabel = computed(() => {
    const book = this.book();
    return book ? formatMoney(book.cost_usd, 'USD') : '';
  });
  protected readonly priceLabel = computed(() => {
    const book = this.book();
    return book && book.selling_price_local !== null && book.selling_currency
      ? formatMoney(book.selling_price_local, book.selling_currency)
      : null;
  });

  constructor() {
    this.loadBook();
  }

  protected reload(): void {
    this.loadBook();
  }

  protected calculatePrice(): void {
    // Una sola petición en vuelo aunque se haga doble clic.
    if (this.calculating() || this.bookId === null) {
      return;
    }
    this.calculating.set(true);
    this.priceResult.set(null);
    this.bookService
      .calculatePrice(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.calculating.set(false);
          this.priceResult.set(result);
          this.book.update((book) =>
            book
              ? {
                  ...book,
                  selling_price_local: result.selling_price_local,
                  selling_currency: result.currency,
                }
              : book,
          );
          this.toasts.success('Precio de venta calculado');
        },
        error: (error: ApiError) => {
          this.calculating.set(false);
          this.toasts.error(apiErrorMessage(error));
        },
      });
  }

  protected askDelete(): void {
    this.confirmingDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmingDelete.set(false);
  }

  protected confirmDelete(): void {
    if (this.deleting() || this.bookId === null) {
      return;
    }
    this.deleting.set(true);
    this.bookService
      .delete(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.finishDelete();
          this.toasts.success('Libro eliminado');
        },
        error: (error: ApiError) => {
          if (error.status === 404) {
            // Otro usuario ya lo había borrado: no hay nada más que hacer aquí.
            this.finishDelete();
            this.toasts.info('El libro ya había sido eliminado');
          } else {
            this.deleting.set(false);
            this.confirmingDelete.set(false);
            this.toasts.error(apiErrorMessage(error));
          }
        },
      });
  }

  private finishDelete(): void {
    this.deleting.set(false);
    this.confirmingDelete.set(false);
    void this.router.navigateByUrl(this.afterDeleteUrl);
  }

  private loadBook(): void {
    if (this.bookId === null) {
      this.loadState.set('not-found');
      return;
    }
    this.loadState.set('loading');
    this.loadError.set(null);
    this.bookService
      .get(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (book) => {
          this.book.set(book);
          this.loadState.set('ready');
        },
        error: (error: ApiError) => {
          this.loadError.set(error);
          this.loadState.set(error.status === 404 ? 'not-found' : 'error');
        },
      });
  }
}
