import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ApiError } from '../../../../core/http/api-error.model';
import { apiErrorMessage } from '../../../../core/http/api-error-message';
import { ToastService } from '../../../../core/ui/toast.service';
import { AlertComponent } from '../../../../shared/components/alert/alert.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { SpinnerComponent } from '../../../../shared/components/spinner/spinner.component';
import { Book, BookPayload } from '../../models/book.model';
import { COUNTRY_OPTIONS } from '../../models/country-options';
import { parseBookId, readBackUrl } from '../../navigation/book-navigation';
import { BookService } from '../../services/book.service';
import {
  CATEGORY_MAX_LENGTH,
  ISBN_MAX_RAW_LENGTH,
  STOCK_MAX,
  TEXT_MAX_LENGTH,
  costValidator,
  formatCostInput,
  parseCostInput,
  countryValidator,
  isbnValidator,
  noNul,
  notBlank,
  stockValidator,
} from '../../validators/book.validators';

// Los controles se llaman igual que los campos de la API (snake_case): field_errors se asigna sin traducir.
type BookForm = FormGroup<{
  title: FormControl<string>;
  author: FormControl<string>;
  isbn: FormControl<string>;
  cost_usd: FormControl<string>;
  stock_quantity: FormControl<number | null>;
  category: FormControl<string>;
  supplier_country: FormControl<string>;
}>;

type FieldName = keyof BookForm['controls'];

type LoadState = 'loading' | 'ready' | 'not-found' | 'error';

const FIELD_ORDER: readonly FieldName[] = [
  'title',
  'author',
  'isbn',
  'cost_usd',
  'stock_quantity',
  'category',
  'supplier_country',
];

const ERROR_MESSAGES: Readonly<Record<FieldName, Readonly<Record<string, string>>>> = {
  title: {
    required: 'El título es obligatorio.',
    maxlength: `El título no puede superar los ${TEXT_MAX_LENGTH} caracteres.`,
    nul: 'El título contiene caracteres no permitidos.',
  },
  author: {
    required: 'El autor es obligatorio.',
    maxlength: `El autor no puede superar los ${TEXT_MAX_LENGTH} caracteres.`,
    nul: 'El autor contiene caracteres no permitidos.',
  },
  isbn: {
    required: 'El ISBN es obligatorio.',
    isbnLength: `El ISBN no puede superar los ${ISBN_MAX_RAW_LENGTH} caracteres.`,
    isbn: 'Introduce un ISBN-10 o ISBN-13 válido, con el dígito verificador correcto.',
  },
  cost_usd: {
    required: 'El costo es obligatorio.',
    cost_format: 'Escribe el costo con coma decimal, por ejemplo 15,99.',
    cost_min: 'El costo mínimo es 0,01 USD.',
    cost_max: 'El costo máximo es 99.999.999,99 USD.',
    cost_decimals: 'El costo admite como máximo 2 decimales.',
  },
  stock_quantity: {
    required: 'El stock es obligatorio.',
    stock_integer: 'El stock debe ser un número entero.',
    stock_min: 'El stock no puede ser negativo.',
    stock_max: `El stock no puede superar ${STOCK_MAX.toLocaleString('es')} unidades.`,
  },
  category: {
    required: 'La categoría es obligatoria.',
    maxlength: `La categoría no puede superar los ${CATEGORY_MAX_LENGTH} caracteres.`,
    nul: 'La categoría contiene caracteres no permitidos.',
  },
  supplier_country: {
    required: 'Selecciona el país del proveedor.',
    country: 'Selecciona un país de la lista.',
  },
};

// Formulario de alta (/books/new) y edición (/books/:id/edit): el modo lo decide el parámetro :id.
@Component({
  selector: 'app-book-form',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent, EmptyStateComponent, SpinnerComponent],
  templateUrl: './book-form.component.html',
  styleUrl: './book-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookFormComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookService = inject(BookService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly countries = COUNTRY_OPTIONS;
  protected readonly textMaxLength = TEXT_MAX_LENGTH;
  protected readonly categoryMaxLength = CATEGORY_MAX_LENGTH;
  protected readonly backUrl = readBackUrl(this.router);
  // UrlTree: un string en routerLink codificaría el "?" de los filtros como parte del path.
  protected readonly backTree = this.router.parseUrl(this.backUrl);

  private readonly rawId = this.route.snapshot.paramMap.get('id');
  protected readonly isEdit = this.rawId !== null;
  private readonly bookId = parseBookId(this.rawId);

  protected readonly loadState = signal<LoadState>(this.isEdit ? 'loading' : 'ready');
  protected readonly loadError = signal<ApiError | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitAttempted = signal(false);

  protected readonly form: BookForm = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(TEXT_MAX_LENGTH), noNul],
    }),
    author: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(TEXT_MAX_LENGTH), noNul],
    }),
    isbn: new FormControl('', { nonNullable: true, validators: [notBlank, isbnValidator] }),
    cost_usd: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, costValidator],
    }),
    stock_quantity: new FormControl<number | null>(null, [Validators.required, stockValidator]),
    category: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(CATEGORY_MAX_LENGTH), noNul],
    }),
    supplier_country: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, countryValidator],
    }),
  });

  constructor() {
    // Al editar un campo se descarta solo su error del servidor (setErrors no se revalida por sí solo).
    for (const name of FIELD_ORDER) {
      const control: AbstractControl = this.form.controls[name];
      control.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (control.hasError('server')) {
          control.updateValueAndValidity({ emitEvent: false });
        }
      });
    }

    if (this.isEdit) {
      this.loadBook();
    }
  }

  protected reload(): void {
    this.loadBook();
  }

  protected submit(): void {
    // Una sola petición en vuelo: el segundo clic/Enter se ignora mientras la primera no termina.
    if (this.submitting()) {
      return;
    }
    this.clearServerErrors();
    this.submitAttempted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }
    this.submitting.set(true);
    this.save().subscribe({
      next: () => this.onSaved(),
      error: (error: ApiError) => this.onSaveError(error),
    });
  }

  protected fieldError(name: FieldName): string | null {
    const control = this.form.controls[name];
    if ((!control.touched && !this.submitAttempted()) || !control.errors) {
      return null;
    }
    const server = control.errors['server'];
    if (typeof server === 'string') {
      return server;
    }
    const messages = ERROR_MESSAGES[name];
    const code = Object.keys(control.errors).find((key) => key in messages);
    return code ? messages[code] : 'El valor no es válido.';
  }

  private save(): Observable<Book> {
    const payload = this.toPayload();
    return this.isEdit && this.bookId !== null
      ? this.bookService.update(this.bookId, payload)
      : this.bookService.create(payload);
  }

  private toPayload(): BookPayload {
    const value = this.form.getRawValue();
    return {
      title: value.title.trim(),
      author: value.author.trim(),
      isbn: value.isbn.trim(),
      cost_usd: parseCostInput(value.cost_usd),
      stock_quantity: Number(value.stock_quantity),
      category: value.category.trim(),
      supplier_country: value.supplier_country.toUpperCase(),
    };
  }

  private onSaved(): void {
    this.submitting.set(false);
    this.toasts.success(this.isEdit ? 'Libro actualizado' : 'Libro creado');
    void this.router.navigateByUrl(this.backUrl);
  }

  private onSaveError(error: ApiError): void {
    this.submitting.set(false);
    const assigned = this.assignFieldErrors(error);
    if (assigned && error.status === 400) {
      this.toasts.error('Revisa los campos marcados en el formulario.');
    } else if (assigned) {
      // 409 de ISBN duplicado: el mensaje va en el campo.
      this.focusFirstInvalid();
    } else {
      this.toasts.error(apiErrorMessage(error));
    }
  }

  // Asigna cada field_error al control del mismo nombre; devuelve true si alguno encajó.
  private assignFieldErrors(error: ApiError): boolean {
    let assigned = false;
    for (const { field, message } of error.field_errors ?? []) {
      const control = this.form.controls[field as FieldName] as
        BookForm['controls'][FieldName] | undefined;
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
        assigned = true;
      }
    }
    if (assigned) {
      this.focusFirstInvalid();
    }
    return assigned;
  }

  private clearServerErrors(): void {
    for (const name of FIELD_ORDER) {
      const control = this.form.controls[name];
      if (control.hasError('server')) {
        control.updateValueAndValidity({ emitEvent: false });
      }
    }
  }

  private focusFirstInvalid(): void {
    const first = FIELD_ORDER.find((name) => this.form.controls[name].invalid);
    if (first) {
      this.host.nativeElement.querySelector<HTMLElement>(`#field-${first}`)?.focus();
    }
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
          this.form.setValue({
            title: book.title,
            author: book.author,
            isbn: book.isbn,
            cost_usd: formatCostInput(book.cost_usd),
            stock_quantity: book.stock_quantity,
            category: book.category,
            supplier_country: book.supplier_country,
          });
          this.loadState.set('ready');
        },
        error: (error: ApiError) => {
          this.loadError.set(error);
          this.loadState.set(error.status === 404 ? 'not-found' : 'error');
        },
      });
  }
}
