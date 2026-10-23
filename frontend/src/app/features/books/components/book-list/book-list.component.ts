import { CurrencyPipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { apiErrorMessage } from '../../../../core/http/api-error-message';
import { ToastService } from '../../../../core/ui/toast.service';
import { AlertComponent } from '../../../../shared/components/alert/alert.component';
import { ConfirmModalComponent } from '../../../../shared/components/confirm-modal/confirm-modal.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PaginationComponent } from '../../../../shared/components/pagination/pagination.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { ApiError } from '../../../../core/http/api-error.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { CountryNamePipe } from '../../../../shared/pipes/country-name.pipe';
import {
  DEFAULT_SORT,
  LOW_STOCK_DEFAULT_SORT,
  LOW_STOCK_THRESHOLD_DEFAULT,
  LOW_STOCK_THRESHOLD_MAX,
  LOW_STOCK_THRESHOLD_MIN,
  PAGE_SIZE_OPTIONS,
} from '../../models/book.constants';
import { Book } from '../../models/book.model';
import {
  BookFilterMode,
  BookListQuery,
  BookSort,
  BookSortField,
  PageRequest,
} from '../../models/book-query.model';
import { BookService } from '../../services/book.service';
import { parseBookListQuery, sameQuery, toQueryParams } from './book-list-query';

type FilterForm = FormGroup<{
  mode: FormControl<BookFilterMode>;
  category: FormControl<string>;
  threshold: FormControl<number | null>;
}>;

interface TableColumn {
  label: string;
  sortField?: BookSortField;
  numeric?: boolean;
  centered?: boolean;
}

interface CategoriesState {
  status: 'loading' | 'loaded' | 'error';
  items: string[];
}

type LoadResult =
  | { query: BookListQuery; page: PageResponse<Book>; error: null }
  | { query: BookListQuery; page: null; error: ApiError };

type StockStatus = 'out' | 'low' | 'ok';

@Component({
  selector: 'app-book-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    DecimalPipe,
    CountryNamePipe,
    AlertComponent,
    ConfirmModalComponent,
    EmptyStateComponent,
    PaginationComponent,
    SpinnerComponent,
    StatusBadgeComponent,
  ],
  templateUrl: './book-list.component.html',
  styleUrl: './book-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookListComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookService = inject(BookService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly thresholdMin = LOW_STOCK_THRESHOLD_MIN;
  protected readonly thresholdMax = LOW_STOCK_THRESHOLD_MAX;
  protected readonly columns: readonly TableColumn[] = [
    { label: 'Título', sortField: 'title' },
    { label: 'ISBN' },
    { label: 'Categoría', sortField: 'category' },
    { label: 'País' },
    { label: 'Stock', sortField: 'stock_quantity', numeric: true },
    { label: 'Costo (USD)', sortField: 'cost_usd', numeric: true },
    { label: 'Precio de venta', numeric: true },
    { label: 'Acciones', centered: true },
  ];

  protected readonly sortableColumns = this.columns.filter((column) => column.sortField);

  // Estado de la URL (fuente única de verdad), ya saneado.
  private readonly query$ = this.route.queryParamMap.pipe(
    map(parseBookListQuery),
    distinctUntilChanged(sameQuery),
  );
  protected readonly query = toSignal(this.query$, {
    initialValue: parseBookListQuery(this.route.snapshot.queryParamMap),
  });

  // Vista actual del listado, enviada como origen a detalle y edición para volver con filtros y página.
  protected readonly fromState = computed(() => ({
    from: this.router.serializeUrl(
      this.router.createUrlTree(['/books'], { queryParams: toQueryParams(this.query()) }),
    ),
  }));

  protected readonly pendingDelete = signal<Book | null>(null);
  protected readonly deleting = signal(false);
  protected readonly calculatingId = signal<number | null>(null);
  protected readonly pageData = signal<PageResponse<Book> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly categoriesState = signal<CategoriesState>({ status: 'loading', items: [] });
  private readonly reload$ = new Subject<void>();

  protected readonly form: FilterForm = new FormGroup({
    mode: new FormControl<BookFilterMode>('all', { nonNullable: true }),
    category: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    threshold: new FormControl<number | null>(LOW_STOCK_THRESHOLD_DEFAULT, {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.min(LOW_STOCK_THRESHOLD_MIN),
        Validators.max(LOW_STOCK_THRESHOLD_MAX),
        Validators.pattern(/^\d+$/),
      ],
    }),
  });
  protected readonly mode = toSignal(
    this.form.controls.mode.valueChanges.pipe(startWith(this.form.controls.mode.value)),
    { requireSync: true },
  );

  protected readonly hasFilters = computed(() => this.query().filter !== 'all');
  protected readonly hasRows = computed(() => (this.pageData()?.content.length ?? 0) > 0);
  protected readonly effectiveSort = computed<BookSort>(() => {
    const query = this.query();
    return query.sort ?? (query.filter === 'low-stock' ? LOW_STOCK_DEFAULT_SORT : DEFAULT_SORT);
  });
  // Incluye la categoría de la URL aunque no esté en la lista (p. ej. escrita en otras mayúsculas).
  protected readonly categoryOptions = computed(() => {
    const items = this.categoriesState().items;
    const current = this.query().category;
    const known = items.some((item) => item.toLowerCase() === current?.toLowerCase());
    return current && !known ? [current, ...items] : items;
  });
  protected readonly errorTitle = computed(() => {
    switch (this.error()?.status) {
      case 503:
        return 'Servicio no disponible';
      // 0: sin red; 502/504: el proxy (ng serve o nginx) no obtuvo respuesta del backend.
      case 0:
      case 502:
      case 504:
        return 'Sin conexión con el servidor';
      default:
        return 'No se pudieron cargar los libros';
    }
  });

  // Sin respuesta del servidor se muestra el mensaje de conexión; en el resto, el del backend.
  protected readonly errorDetail = computed(() => {
    const error = this.error();
    if (!error) {
      return null;
    }
    return [0, 502, 504].includes(error.status) ? apiErrorMessage(error) : error.message;
  });

  constructor() {
    combineLatest([this.query$, this.reload$.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        // switchMap cancela la petición anterior; el catchError interno mantiene vivo el flujo.
        switchMap(([query]) =>
          this.fetch(query).pipe(
            map((page): LoadResult => ({ query, page, error: null })),
            catchError((error: ApiError) => of<LoadResult>({ query, page: null, error })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => this.handleResult(result));

    this.bookService
      .categories()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (items) => this.categoriesState.set({ status: 'loaded', items }),
        error: () => this.categoriesState.set({ status: 'error', items: [] }),
      });

    // La URL manda: al navegar (atrás/adelante, enlace compartido) el formulario refleja la vista.
    effect(() => {
      const query = this.query();
      this.form.setValue({
        mode: query.filter,
        category: query.category ?? '',
        threshold: query.threshold ?? LOW_STOCK_THRESHOLD_DEFAULT,
      });
    });

    // Solo se valida el control del modo activo; el desplegable se deshabilita si no hay categorías.
    effect(() => {
      const mode = this.mode();
      const categoriesUnavailable = this.categoriesState().status !== 'loaded';
      const { category, threshold } = this.form.controls;
      toggleControl(category, mode === 'category' && !categoriesUnavailable);
      toggleControl(threshold, mode === 'low-stock');
    });
  }

  protected applyFilters(): void {
    const { mode, category, threshold } = this.form.getRawValue();
    if (mode === 'category' && (this.form.controls.category.invalid || !category.trim())) {
      this.form.controls.category.markAsTouched();
      return;
    }
    if (mode === 'low-stock' && this.form.controls.threshold.invalid) {
      this.form.controls.threshold.markAsTouched();
      return;
    }
    this.navigate({
      ...this.query(),
      page: 0,
      filter: mode,
      category: mode === 'category' ? category.trim() : null,
      threshold: mode === 'low-stock' ? threshold : null,
    });
  }

  protected clearFilters(): void {
    this.form.reset();
    this.navigate({ ...this.query(), page: 0, filter: 'all', category: null, threshold: null });
  }

  protected askDelete(book: Book): void {
    this.pendingDelete.set(book);
  }

  protected cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  protected confirmDelete(): void {
    const book = this.pendingDelete();
    // Una sola petición en vuelo aunque se haga doble clic en "Eliminar".
    if (!book || this.deleting()) {
      return;
    }
    this.deleting.set(true);
    this.bookService
      .delete(book.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.closeDeleteModal();
          this.toasts.success('Libro eliminado');
          this.refreshAfterRemoval();
        },
        error: (error: ApiError) => {
          this.closeDeleteModal();
          if (error.status === 404) {
            // Otro usuario ya lo había borrado: se informa y se refresca el listado.
            this.toasts.info('El libro ya había sido eliminado');
            this.refreshAfterRemoval();
          } else {
            this.toasts.error(apiErrorMessage(error));
          }
        },
      });
  }

  protected calculatePrice(book: Book): void {
    // Una sola petición en vuelo aunque se haga doble clic.
    if (this.calculatingId() !== null) {
      return;
    }
    this.calculatingId.set(book.id);
    this.bookService
      .calculatePrice(book.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.calculatingId.set(null);
          const unchanged =
            book.selling_price_local === result.selling_price_local &&
            book.selling_currency === result.currency;
          this.pageData.update((page) =>
            page
              ? {
                  ...page,
                  content: page.content.map((item) =>
                    item.id === book.id
                      ? {
                          ...item,
                          selling_price_local: result.selling_price_local,
                          selling_currency: result.currency,
                        }
                      : item,
                  ),
                }
              : page,
          );
          if (unchanged) {
            this.toasts.info(`No hubo cambios en el precio de «${book.title}»`);
          } else {
            this.toasts.success(`Precio de «${book.title}» calculado`);
          }
        },
        error: (error: ApiError) => {
          this.calculatingId.set(null);
          this.toasts.error(apiErrorMessage(error));
        },
      });
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected sortBy(field: BookSortField): void {
    const current = this.effectiveSort();
    const direction = current.field === field && current.direction === 'asc' ? 'desc' : 'asc';
    this.navigate({ ...this.query(), page: 0, sort: { field, direction } });
  }

  protected onSortFieldChange(event: Event): void {
    const field = (event.target as HTMLSelectElement).value as BookSortField;
    this.navigate({ ...this.query(), page: 0, sort: { field, direction: 'asc' } });
  }

  protected ariaSort(field: BookSortField): 'ascending' | 'descending' | 'none' {
    const sort = this.effectiveSort();
    if (sort.field !== field) {
      return 'none';
    }
    return sort.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected sortIcon(field: BookSortField): string {
    const state = this.ariaSort(field);
    return state === 'ascending' ? '▲' : state === 'descending' ? '▼' : '↕';
  }

  protected onPageChange(page: number): void {
    this.navigate({ ...this.query(), page });
  }

  protected onSizeChange(size: number): void {
    this.navigate({ ...this.query(), page: 0, size });
  }

  protected stockStatus(book: Book): StockStatus {
    if (book.stock_quantity === 0) {
      return 'out';
    }
    return book.stock_quantity <= LOW_STOCK_THRESHOLD_DEFAULT ? 'low' : 'ok';
  }

  protected thresholdError(): string | null {
    const control = this.form.controls.threshold;
    if (control.disabled || !control.touched || !control.errors) {
      return null;
    }
    if (control.errors['required']) {
      return 'Indica un umbral.';
    }
    if (control.errors['min'] || control.errors['max']) {
      return `El umbral debe estar entre ${LOW_STOCK_THRESHOLD_MIN} y ${LOW_STOCK_THRESHOLD_MAX.toLocaleString('es')}.`;
    }
    return 'El umbral debe ser un número entero.';
  }

  protected categoryError(): string | null {
    const control = this.form.controls.category;
    return control.enabled && control.touched && control.invalid
      ? 'Selecciona una categoría.'
      : null;
  }

  private fetch(query: BookListQuery): Observable<PageResponse<Book>> {
    const request: PageRequest = { page: query.page, size: query.size, sort: query.sort };
    switch (query.filter) {
      case 'category':
        return this.bookService.search(query.category ?? '', request);
      case 'low-stock':
        return this.bookService.lowStock(query.threshold, request);
      default:
        return this.bookService.list(request);
    }
  }

  private handleResult(result: LoadResult): void {
    this.loading.set(false);
    if (result.error) {
      this.error.set(result.error);
      return;
    }
    const { page, query } = result;
    // Página fuera de rango (p. ej. enlace antiguo tras borrar libros): se salta a la última.
    if (page.content.length === 0 && page.total_elements > 0 && query.page > 0) {
      this.navigate({ ...query, page: Math.max(page.total_pages - 1, 0) }, true);
      return;
    }
    this.pageData.set(page);
  }

  private closeDeleteModal(): void {
    this.deleting.set(false);
    this.pendingDelete.set(null);
  }

  // Si se vació la página (y no es la primera) se retrocede una; si no, se recarga la actual.
  private refreshAfterRemoval(): void {
    const query = this.query();
    if ((this.pageData()?.content.length ?? 0) <= 1 && query.page > 0) {
      this.navigate({ ...query, page: query.page - 1 });
    } else {
      this.reload$.next();
    }
  }

  private navigate(query: BookListQuery, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toQueryParams(query),
      replaceUrl,
    });
  }
}

function toggleControl(control: AbstractControl, enabled: boolean): void {
  if (enabled && control.disabled) {
    control.enable({ emitEvent: false });
  } else if (!enabled && control.enabled) {
    control.disable({ emitEvent: false });
  }
}
