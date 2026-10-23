import { Routes } from '@angular/router';

import { bookListQueryGuard } from './components/book-list/book-list-query.guard';

export const BOOKS_ROUTES: Routes = [
  {
    path: '',
    title: 'Libros · Inventario de Librería',
    canActivate: [bookListQueryGuard],
    // El guard también se evalúa cuando solo cambian los query params.
    runGuardsAndResolvers: 'paramsOrQueryParamsChange',
    loadComponent: () =>
      import('./components/book-list/book-list.component').then((m) => m.BookListComponent),
  },
];
