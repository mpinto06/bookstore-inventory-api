import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { errorInterceptor } from '../../../../core/http/error.interceptor';
import { ApiError } from '../../../../shared/models/api-error.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { BOOKS_ROUTES } from '../../books.routes';
import { Book } from '../../models/book.model';
import { BookListComponent } from './book-list.component';

function book(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    title: 'Cien años de soledad',
    author: 'Gabriel García Márquez',
    isbn: '9780307474728',
    cost_usd: 12.5,
    selling_price_local: null,
    selling_currency: null,
    stock_quantity: 25,
    category: 'Novela',
    supplier_country: 'CO',
    created_at: '2026-10-22T14:19:20.248Z',
    updated_at: '2026-10-22T14:19:20.248Z',
    ...overrides,
  };
}

function page(content: Book[], overrides: Partial<PageResponse<Book>> = {}): PageResponse<Book> {
  return {
    content,
    page: 0,
    size: 20,
    total_elements: content.length,
    total_pages: content.length > 0 ? 1 : 0,
    ...overrides,
  };
}

describe('BookListComponent', () => {
  let fixture: ComponentFixture<unknown>;
  let http: HttpTestingController;
  let router: Router;

  // Navega a la URL dada con el enrutado real de la feature (guard incluido) y responde categorías.
  async function setup(url = '/books', categories: string[] | 'error' = ['Ciencia', 'Historia']) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'books', children: BOOKS_ROUTES }]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, BookListComponent);
    fixture = harness.fixture;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);

    const categoriesRequest = http.expectOne('/api/books/categories');
    if (categories === 'error') {
      categoriesRequest.flush(null, { status: 500, statusText: 'Internal Server Error' });
    } else {
      categoriesRequest.flush(categories);
    }
    fixture.detectChanges();
  }

  function expectBooksRequest(path: '' | '/search' | '/low-stock' = ''): TestRequest {
    return http.expectOne((req) => req.url === `/api/books${path}`);
  }

  function respond(request: TestRequest, body: PageResponse<Book>): void {
    request.flush(body);
    fixture.detectChanges();
  }

  function query<T extends HTMLElement>(testId: string): T | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function get<T extends HTMLElement>(testId: string): T {
    const element = query<T>(testId);
    if (!element) {
      throw new Error(`No existe [data-testid="${testId}"]`);
    }
    return element;
  }

  function all(testId: string): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`));
  }

  function selectOption(testId: string, value: string): void {
    const select = get<HTMLSelectElement>(testId);
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function typeInto(testId: string, value: string): void {
    const input = get<HTMLInputElement>(testId);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function click(testId: string): Promise<void> {
    get(testId).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('muestra una fila por libro con autor, estado de stock y precio', async () => {
    await setup();
    expect(get('books-loading')).toBeTruthy();

    respond(
      expectBooksRequest(),
      page([
        book({ id: 1, stock_quantity: 0 }),
        book({ id: 2, title: 'Rayuela', author: 'Julio Cortázar', stock_quantity: 10 }),
        book({ id: 3, stock_quantity: 11, selling_price_local: 19.03, selling_currency: 'EUR' }),
      ]),
    );

    const rows = all('book-row');
    expect(rows).toHaveLength(3);
    expect(rows[1].textContent).toContain('Rayuela');
    expect(rows[1].textContent).toContain('Julio Cortázar');
    const stock = all('book-stock').map((cell) => cell.textContent?.replace(/\s+/g, ' ').trim());
    expect(stock).toEqual(['Sin stock', '10 Stock bajo', '11']);
    const prices = all('book-price').map((cell) => cell.textContent ?? '');
    expect(prices[0]).toContain('Aún no calculado');
    expect(prices[2]).toContain('19.03');
    expect(query('books-loading')).toBeNull();
  });

  it('muestra "Aún no hay libros" cuando no hay datos ni filtros', async () => {
    await setup();

    respond(expectBooksRequest(), page([]));

    expect(get('empty-all').textContent).toContain('Aún no hay libros');
    expect(query('books-table')).toBeNull();
  });

  it('muestra "Sin resultados para este filtro" y permite limpiar los filtros', async () => {
    await setup('/books?filter=category&category=Poes%C3%ADa');

    respond(expectBooksRequest('/search'), page([]));
    expect(get('empty-filtered').textContent).toContain('Sin resultados para este filtro');

    await click('empty-clear');

    expect(router.url).toBe('/books');
    respond(expectBooksRequest(), page([book()]));
    expect(all('book-row')).toHaveLength(1);
  });

  it('muestra el error con "Reintentar" y al pulsarlo vuelve a pedir los libros', async () => {
    await setup();
    const serviceUnavailable: ApiError = {
      timestamp: '2026-10-22T14:19:20.248Z',
      status: 503,
      error: 'Service Unavailable',
      message: 'La base de datos no está disponible',
      path: '/books',
    };

    expectBooksRequest().flush(serviceUnavailable, {
      status: 503,
      statusText: 'Service Unavailable',
    });
    fixture.detectChanges();

    expect(get('alert-title').textContent).toContain('Servicio no disponible');
    expect(get('alert-message').textContent).toContain('La base de datos no está disponible');

    await click('alert-retry');

    respond(expectBooksRequest(), page([book()]));
    expect(query('books-error')).toBeNull();
    expect(all('book-row')).toHaveLength(1);
  });

  it('al aplicar el filtro por categoría actualiza la URL y pide /books/search desde la página 0', async () => {
    await setup('/books?page=1');
    respond(expectBooksRequest(), page([book()], { page: 1, total_elements: 21, total_pages: 2 }));

    selectOption('filter-mode', 'category');
    selectOption('filter-category', 'Historia');
    await click('filter-apply');

    expect(router.url).toBe('/books?filter=category&category=Historia');
    const request = expectBooksRequest('/search');
    expect(request.request.params.get('category')).toBe('Historia');
    expect(request.request.params.get('page')).toBe('0');
    respond(request, page([book({ category: 'Historia' })]));
  });

  it('no envía la petición si el umbral de stock bajo es inválido', async () => {
    await setup();
    respond(expectBooksRequest(), page([book()]));

    selectOption('filter-mode', 'low-stock');
    typeInto('filter-threshold', '-1');
    await click('filter-apply');

    expect(get('threshold-error').textContent).toContain('entre 0 y 1.000.000');
    expect(router.url).toBe('/books');
    http.expectNone((req) => req.url === '/api/books/low-stock');
  });

  it('aplica el filtro de stock bajo con un umbral válido', async () => {
    await setup();
    respond(expectBooksRequest(), page([book()]));

    selectOption('filter-mode', 'low-stock');
    typeInto('filter-threshold', '5');
    await click('filter-apply');

    expect(router.url).toBe('/books?filter=low-stock&threshold=5');
    const request = expectBooksRequest('/low-stock');
    expect(request.request.params.get('threshold')).toBe('5');
    respond(request, page([book({ stock_quantity: 3 })]));
  });

  it('al pulsar un encabezado ordenable pide el sort correcto y alterna asc/desc', async () => {
    await setup();
    respond(expectBooksRequest(), page([book()]));

    await click('sort-title');
    const ascending = expectBooksRequest();
    expect(ascending.request.params.get('sort')).toBe('title,asc');
    respond(ascending, page([book()]));
    expect(get('sort-title').closest('th')?.getAttribute('aria-sort')).toBe('ascending');

    await click('sort-title');
    const descending = expectBooksRequest();
    expect(descending.request.params.get('sort')).toBe('title,desc');
    respond(descending, page([book()]));

    expect(router.url).toBe('/books?sort=title,desc');
    expect(get('sort-title').closest('th')?.getAttribute('aria-sort')).toBe('descending');
  });

  it('sanea los query params inválidos antes de pedir datos', async () => {
    await setup('/books?page=-3&size=7&sort=hack,asc&filter=low-stock&threshold=999999999');

    const request = expectBooksRequest('/low-stock');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('threshold')).toBe('10');
    expect(request.request.params.has('sort')).toBe(false);
    expect(router.url).toBe('/books?filter=low-stock&threshold=10');
    respond(request, page([]));
  });

  it('conserva la vista indicada en la URL (página, tamaño, orden y filtro)', async () => {
    await setup('/books?page=2&size=50&sort=cost_usd,desc&filter=category&category=Ciencia');

    const request = expectBooksRequest('/search');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.get('sort')).toBe('cost_usd,desc');
    expect(request.request.params.get('category')).toBe('Ciencia');
    respond(request, page([book()], { page: 2, size: 50, total_elements: 101, total_pages: 3 }));

    expect(get<HTMLSelectElement>('filter-mode').value).toBe('category');
    expect(get<HTMLSelectElement>('filter-category').value).toBe('Ciencia');
    expect(get('pagination-indicator').textContent?.trim()).toBe('Página 3 de 3');
  });

  it('deshabilita el desplegable de categorías con un aviso si no se pudieron cargar', async () => {
    await setup('/books', 'error');
    respond(expectBooksRequest(), page([book()]));

    selectOption('filter-mode', 'category');

    expect(get<HTMLSelectElement>('filter-category').disabled).toBe(true);
    expect(get('categories-error').textContent).toContain('No se pudieron cargar las categorías');
    expect(all('book-row')).toHaveLength(1);
  });

  it('cambiar el tamaño de página vuelve a la página 0', async () => {
    await setup('/books?page=1');
    respond(expectBooksRequest(), page([book()], { page: 1, total_elements: 30, total_pages: 2 }));

    selectOption('pagination-size', '50');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/books?size=50');
    const request = expectBooksRequest();
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('50');
    respond(request, page([book()], { size: 50 }));
  });
});
