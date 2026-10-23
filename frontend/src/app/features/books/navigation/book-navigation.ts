import { Router } from '@angular/router';

export const BOOKS_ROOT = '/books';

// Id de ruta válido: entero positivo que cabe en un Long de forma segura. Cualquier otra cosa se trata como 404.
export function parseBookId(raw: string | null): number | null {
  if (raw === null || !/^[1-9]\d{0,14}$/.test(raw)) {
    return null;
  }
  return Number(raw);
}

// URL a la que volver tras guardar o cancelar: la recibida en `state.from` si pertenece a /books
// (conserva filtros y página) y, si no, el listado.
export function resolveBackUrl(state: unknown): string {
  const from =
    typeof state === 'object' && state !== null ? (state as { from?: unknown }).from : null;
  if (typeof from === 'string' && isBooksUrl(from)) {
    return from;
  }
  return BOOKS_ROOT;
}

function isBooksUrl(url: string): boolean {
  if (!url.startsWith(BOOKS_ROOT)) {
    return false;
  }
  // "/books", "/books?...", "/books/..."; no "/booksfoo" ni "/books//host".
  const next = url.charAt(BOOKS_ROOT.length);
  return (
    (next === '' || next === '/' || next === '?' || next === '#') &&
    !url.startsWith('//', BOOKS_ROOT.length)
  );
}

// Lee el origen de la navegación en curso (state.from); al recargar la página lo recupera de history.state.
export function readBackUrl(router: Router): string {
  return resolveBackUrl(router.currentNavigation()?.extras.state ?? history.state);
}
