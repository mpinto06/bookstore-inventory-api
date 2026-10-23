import { ApiError } from './api-error.model';

export const NETWORK_ERROR_MESSAGE = 'No se pudo conectar con el servidor';

const STATUS_MESSAGES: Readonly<Record<number, string>> = {
  0: NETWORK_ERROR_MESSAGE,
  400: 'Los datos enviados no son válidos. Revísalos e inténtalo de nuevo',
  401: 'Necesitas iniciar sesión para continuar',
  403: 'No tienes permiso para realizar esta acción',
  404: 'El recurso solicitado no existe',
  409: 'Ya existe un registro que entra en conflicto con estos datos',
  422: 'El país del proveedor no tiene una moneda asociada: edita el libro y elige otro país',
  500: 'Ocurrió un error en el servidor. Inténtalo de nuevo más tarde',
  502: 'El servidor no respondió correctamente',
  503: 'Servicio externo no disponible, intenta de nuevo en un momento',
  504: 'El servidor tardó demasiado en responder',
};

// Mensaje en español para un código HTTP. Nunca incluye detalles internos del servidor.
export function messageForStatus(status: number): string {
  return STATUS_MESSAGES[status] ?? `Ocurrió un error inesperado (código ${status})`;
}

// Mensaje para mostrar al usuario (toast, alerta) a partir de un ApiError.
export function apiErrorMessage(error: ApiError): string {
  // Sin respuesta del backend (0) o sin respuesta del proxy/nginx (502, 504): para el usuario es lo mismo.
  if (error.status === 0 || error.status === 502 || error.status === 504) {
    return NETWORK_ERROR_MESSAGE;
  }
  return messageForStatus(error.status);
}
