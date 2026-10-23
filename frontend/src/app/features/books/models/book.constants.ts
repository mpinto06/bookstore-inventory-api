import { BookSort } from './book-query.model';

export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 20, 50, 100];
export const DEFAULT_PAGE_SIZE = 20;

export const LOW_STOCK_THRESHOLD_DEFAULT = 10;
export const LOW_STOCK_THRESHOLD_MIN = 0;
export const LOW_STOCK_THRESHOLD_MAX = 1_000_000;

// Límites del backend: category máx. 100 caracteres; page * size <= Integer.MAX_VALUE.
export const CATEGORY_MAX_LENGTH = 100;
export const MAX_OFFSET = 2_147_483_647;

// Orden que aplica el backend cuando no se envía sort.
export const DEFAULT_SORT: BookSort = { field: 'id', direction: 'asc' };
export const LOW_STOCK_DEFAULT_SORT: BookSort = { field: 'stock_quantity', direction: 'asc' };
