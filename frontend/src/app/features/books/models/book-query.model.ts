// Lista blanca de campos de ordenamiento: la misma que acepta el backend en "sort".
export const BOOK_SORT_FIELDS = [
  'id',
  'title',
  'author',
  'category',
  'cost_usd',
  'stock_quantity',
  'created_at',
] as const;

export type BookSortField = (typeof BOOK_SORT_FIELDS)[number];

export type SortDirection = 'asc' | 'desc';

export interface BookSort {
  field: BookSortField;
  direction: SortDirection;
}

// Parámetros de paginación comunes a list, search y lowStock; los ausentes no se envían.
export interface PageRequest {
  page?: number | null;
  size?: number | null;
  sort?: BookSort | null;
}

export type BookFilterMode = 'all' | 'category' | 'low-stock';

// Estado del listado (derivado de los query params de la URL, ya saneado).
export interface BookListQuery {
  // 0-based
  page: number;
  size: number;
  // null = orden por defecto del backend para el endpoint (id asc; stock asc en stock bajo)
  sort: BookSort | null;
  filter: BookFilterMode;
  // Solo con filter = 'category'
  category: string | null;
  // Solo con filter = 'low-stock'
  threshold: number | null;
}
