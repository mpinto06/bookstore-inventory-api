import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page-response.model';
import { Book } from '../models/book.model';
import { PageRequest } from '../models/book-query.model';

@Injectable({ providedIn: 'root' })
export class BookService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/books`;

  list(request: PageRequest = {}): Observable<PageResponse<Book>> {
    return this.http.get<PageResponse<Book>>(this.baseUrl, { params: toParams(request) });
  }

  search(category: string, request: PageRequest = {}): Observable<PageResponse<Book>> {
    return this.http.get<PageResponse<Book>>(`${this.baseUrl}/search`, {
      params: toParams(request, { category: category.trim() }),
    });
  }

  lowStock(threshold: number | null, request: PageRequest = {}): Observable<PageResponse<Book>> {
    return this.http.get<PageResponse<Book>>(`${this.baseUrl}/low-stock`, {
      params: toParams(request, { threshold }),
    });
  }

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/categories`);
  }
}

// Construye los HttpParams omitiendo null, undefined y cadenas vacías.
function toParams(
  request: PageRequest,
  extra: Record<string, string | number | null | undefined> = {},
): HttpParams {
  const { page, size, sort } = request;
  const entries: Record<string, string | number | null | undefined> = {
    ...extra,
    page,
    size,
    sort: sort ? `${sort.field},${sort.direction}` : null,
  };
  let params = new HttpParams();
  for (const [key, value] of Object.entries(entries)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
