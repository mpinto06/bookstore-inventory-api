import { Routes } from '@angular/router';

import { bookListQueryGuard } from './components/book-list/book-list-query.guard';

const TITLE_SUFFIX = 'Inventario de Librería';

// "new" va antes que ":id". Un id no numérico llega al componente, que muestra un 404 con enlace al listado.
export const BOOKS_ROUTES: Routes = [
  {
    path: '',
    title: `Libros · ${TITLE_SUFFIX}`,
    canActivate: [bookListQueryGuard],
    // El guard también se evalúa cuando solo cambian los query params.
    runGuardsAndResolvers: 'paramsOrQueryParamsChange',
    loadComponent: () =>
      import('./components/book-list/book-list.component').then((m) => m.BookListComponent),
  },
  {
    path: 'new',
    title: `Nuevo libro · ${TITLE_SUFFIX}`,
    loadComponent: () =>
      import('./components/book-form/book-form.component').then((m) => m.BookFormComponent),
  },
  {
    path: ':id',
    title: `Detalle del libro · ${TITLE_SUFFIX}`,
    loadComponent: () =>
      import('./components/book-detail/book-detail.component').then((m) => m.BookDetailComponent),
  },
  {
    path: ':id/edit',
    title: `Editar libro · ${TITLE_SUFFIX}`,
    loadComponent: () =>
      import('./components/book-form/book-form.component').then((m) => m.BookFormComponent),
  },
];
