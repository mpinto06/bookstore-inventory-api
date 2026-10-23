// Libro tal como lo devuelve la API (snake_case, sin capa de mapeo).
export interface Book {
  id: number;
  title: string;
  author: string;
  // Normalizado, sin guiones
  isbn: string;
  cost_usd: number;
  // null hasta ejecutar calculate-price (y de nuevo tras un PUT que cambie costo o país)
  selling_price_local: number | null;
  // ISO 4217; null cuando el precio es null
  selling_currency: string | null;
  stock_quantity: number;
  category: string;
  // ISO 3166-1 alpha-2
  supplier_country: string;
  // ISO-8601 (UTC)
  created_at: string;
  updated_at: string;
}
