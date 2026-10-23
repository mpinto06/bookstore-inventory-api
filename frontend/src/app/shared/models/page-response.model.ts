// Página genérica de los endpoints paginados (snake_case, tal como llega en el JSON).
export interface PageResponse<T> {
  content: T[];
  // 0-based
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}
