import { Routes } from '@angular/router';

import { MainLayoutComponent } from './layout/main-layout/main-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'books' },
      {
        path: 'books',
        loadChildren: () => import('./features/books/books.routes').then((m) => m.BOOKS_ROUTES),
      },
      { path: '**', redirectTo: 'books' },
    ],
  },
];
