import { ParamMap } from '@angular/router';

import {
  CATEGORY_MAX_LENGTH,
  DEFAULT_PAGE_SIZE,
  LOW_STOCK_THRESHOLD_DEFAULT,
  LOW_STOCK_THRESHOLD_MAX,
  MAX_OFFSET,
  PAGE_SIZE_OPTIONS,
} from '../../models/book.constants';
import {
  BOOK_SORT_FIELDS,
  BookFilterMode,
  BookListQuery,
  BookSort,
  BookSortField,
} from '../../models/book-query.model';

// La URL es la fuente única de verdad del listado. Estas funciones la traducen a un BookListQuery
// válido (todo valor inválido se sustituye por su valor por defecto, así nunca se envía al backend una
// petición que se sabe inválida) y de vuelta a query params canónicos (sin los valores por defecto).

export function parseBookListQuery(params: ParamMap): BookListQuery {
  const size = parseSize(params.get('size'));
  let filter = parseFilter(params.get('filter'));
  const category = filter === 'category' ? parseCategory(params.get('category')) : null;
  if (filter === 'category' && category === null) {
    filter = 'all';
  }
  let page = parseNonNegativeInt(params.get('page')) ?? 0;
  if (page * size > MAX_OFFSET) {
    page = 0;
  }
  return {
    page,
    size,
    sort: parseSort(params.get('sort')),
    filter,
    category,
    threshold: filter === 'low-stock' ? parseThreshold(params.get('threshold')) : null,
  };
}

export function toQueryParams(query: BookListQuery): Record<string, string> {
  const params: Record<string, string> = {};
  if (query.page > 0) {
    params['page'] = String(query.page);
  }
  if (query.size !== DEFAULT_PAGE_SIZE) {
    params['size'] = String(query.size);
  }
  if (query.sort) {
    params['sort'] = `${query.sort.field},${query.sort.direction}`;
  }
  if (query.filter === 'category' && query.category) {
    params['filter'] = 'category';
    params['category'] = query.category;
  }
  if (query.filter === 'low-stock') {
    params['filter'] = 'low-stock';
    params['threshold'] = String(query.threshold ?? LOW_STOCK_THRESHOLD_DEFAULT);
  }
  return params;
}

// true si los query params actuales ya son exactamente los canónicos.
export function isCanonical(params: ParamMap, canonical: Record<string, string>): boolean {
  const keys = Object.keys(canonical);
  return (
    params.keys.length === keys.length &&
    keys.every((key) => params.getAll(key).length === 1 && params.get(key) === canonical[key])
  );
}

export function sameQuery(a: BookListQuery, b: BookListQuery): boolean {
  return JSON.stringify(toQueryParams(a)) === JSON.stringify(toQueryParams(b));
}

function parseNonNegativeInt(raw: string | null): number | null {
  if (raw === null || !/^\d{1,10}$/.test(raw)) {
    return null;
  }
  return Number(raw);
}

function parseSize(raw: string | null): number {
  const size = parseNonNegativeInt(raw);
  return size !== null && PAGE_SIZE_OPTIONS.includes(size) ? size : DEFAULT_PAGE_SIZE;
}

function parseFilter(raw: string | null): BookFilterMode {
  return raw === 'category' || raw === 'low-stock' ? raw : 'all';
}

function parseCategory(raw: string | null): string | null {
  const category = raw?.trim() ?? '';
  return category.length > 0 && category.length <= CATEGORY_MAX_LENGTH ? category : null;
}

function parseThreshold(raw: string | null): number {
  const threshold = parseNonNegativeInt(raw);
  return threshold !== null && threshold <= LOW_STOCK_THRESHOLD_MAX
    ? threshold
    : LOW_STOCK_THRESHOLD_DEFAULT;
}

function parseSort(raw: string | null): BookSort | null {
  if (!raw) {
    return null;
  }
  const parts = raw.split(',');
  if (parts.length > 2) {
    return null;
  }
  const field = parts[0].trim();
  const direction = (parts[1] ?? 'asc').trim().toLowerCase();
  if (!isSortField(field) || (direction !== 'asc' && direction !== 'desc')) {
    return null;
  }
  return { field, direction };
}

function isSortField(value: string): value is BookSortField {
  return (BOOK_SORT_FIELDS as readonly string[]).includes(value);
}
