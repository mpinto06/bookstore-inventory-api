import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '../../shared/models/api-error.model';

export const NETWORK_ERROR_MESSAGE = 'No se pudo conectar con el servidor';

const STATUS_MESSAGES: Readonly<Record<number, string>> = {
  400: 'La petición no es válida',
  401: 'Necesitas iniciar sesión para continuar',
  403: 'No tienes permiso para realizar esta acción',
  404: 'El recurso solicitado no existe',
  409: 'La operación entra en conflicto con el estado actual del recurso',
  422: 'La operación no se puede procesar con los datos actuales',
  500: 'Error interno del servidor',
  502: 'El servidor no respondió correctamente',
  503: 'El servicio no está disponible en este momento. Inténtalo de nuevo más tarde',
  504: 'El servidor tardó demasiado en responder',
};

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
    message: status === 0 ? NETWORK_ERROR_MESSAGE : genericMessage(status),
    path: requestPath,
  };
}

function genericMessage(status: number): string {
  return STATUS_MESSAGES[status] ?? `Ocurrió un error inesperado (código ${status})`;
}

function isApiError(body: unknown): body is ApiError {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const candidate = body as Partial<Record<keyof ApiError, unknown>>;
  return typeof candidate.status === 'number' && typeof candidate.message === 'string';
}
