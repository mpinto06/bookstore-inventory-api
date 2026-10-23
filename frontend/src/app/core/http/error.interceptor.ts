import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { ApiError } from './api-error.model';
import { NETWORK_ERROR_MESSAGE, messageForStatus } from './api-error-message';

// Convierte cualquier HttpErrorResponse en un ApiError: los consumidores nunca ven el error crudo.
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) =>
      throwError(() => (error instanceof HttpErrorResponse ? toApiError(error, req.url) : error)),
    ),
  );

export function toApiError(response: HttpErrorResponse, requestPath: string): ApiError {
  if (isApiError(response.error)) {
    return response.error;
  }
  const status = response.status;
  return {
    timestamp: new Date().toISOString(),
    status,
    error: status === 0 ? 'Network Error' : response.statusText || 'Error',
    message: status === 0 ? NETWORK_ERROR_MESSAGE : messageForStatus(status),
    path: requestPath,
  };
}

function isApiError(body: unknown): body is ApiError {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const candidate = body as Partial<Record<keyof ApiError, unknown>>;
  return typeof candidate.status === 'number' && typeof candidate.message === 'string';
}
