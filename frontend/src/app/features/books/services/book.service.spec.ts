import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PageResponse } from '../../../shared/models/page-response.model';
import { Book } from '../models/book.model';
import { BookService } from './book.service';

const EMPTY_PAGE: PageResponse<Book> = {
  content: [],
  page: 0,
  size: 20,
  total_elements: 0,
  total_pages: 0,
};

describe('BookService', () => {
  let service: BookService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('list', () => {
    it('pide GET /api/books con page, size y sort en formato "campo,dir"', () => {
      service
        .list({ page: 2, size: 50, sort: { field: 'cost_usd', direction: 'desc' } })
        .subscribe();

      const req = http.expectOne((r) => r.url === '/api/books');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().sort()).toEqual(['page', 'size', 'sort']);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('sort')).toBe('cost_usd,desc');
      req.flush(EMPTY_PAGE);
    });

    it('no envía parámetros nulos o ausentes', () => {
      service.list({ page: null, size: undefined, sort: null }).subscribe();

      const req = http.expectOne('/api/books');
      expect(req.request.params.keys()).toEqual([]);
      req.flush(EMPTY_PAGE);
    });

    it('envía page=0 (un cero no se considera vacío)', () => {
      service.list({ page: 0 }).subscribe();

      const req = http.expectOne((r) => r.url === '/api/books');
      expect(req.request.params.get('page')).toBe('0');
      req.flush(EMPTY_PAGE);
    });
  });

  describe('search', () => {
    it('pide GET /api/books/search con la categoría recortada y la paginación', () => {
      service.search('  Historia  ', { page: 1, size: 10 }).subscribe();

      const req = http.expectOne((r) => r.url === '/api/books/search');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().sort()).toEqual(['category', 'page', 'size']);
      expect(req.request.params.get('category')).toBe('Historia');
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('size')).toBe('10');
      req.flush(EMPTY_PAGE);
    });

    it('no envía una categoría vacía', () => {
      service.search('   ').subscribe();

      const req = http.expectOne('/api/books/search');
      expect(req.request.params.has('category')).toBe(false);
      req.flush(EMPTY_PAGE);
    });
  });

  describe('lowStock', () => {
    it('pide GET /api/books/low-stock con threshold, paginación y sort', () => {
      service
        .lowStock(5, { page: 0, size: 20, sort: { field: 'title', direction: 'asc' } })
        .subscribe();

      const req = http.expectOne((r) => r.url === '/api/books/low-stock');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('threshold')).toBe('5');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      expect(req.request.params.get('sort')).toBe('title,asc');
      req.flush(EMPTY_PAGE);
    });

    it('envía threshold=0 y omite el umbral nulo', () => {
      service.lowStock(0).subscribe();
      service.lowStock(null).subscribe();

      const requests = http.match((r) => r.url === '/api/books/low-stock');
      expect(requests.map((r) => r.request.params.get('threshold'))).toEqual(['0', null]);
      expect(requests[1].request.params.keys()).toEqual([]);
      requests.forEach((r) => r.flush(EMPTY_PAGE));
    });
  });

  describe('categories', () => {
    it('pide GET /api/books/categories sin parámetros y devuelve la lista', () => {
      let result: string[] | undefined;
      service.categories().subscribe((categories) => (result = categories));

      const req = http.expectOne('/api/books/categories');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys()).toEqual([]);
      req.flush(['Ciencia', 'Historia']);

      expect(result).toEqual(['Ciencia', 'Historia']);
    });
  });
});
