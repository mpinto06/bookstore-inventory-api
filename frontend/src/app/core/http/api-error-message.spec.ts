import { ApiError } from './api-error.model';
import { NETWORK_ERROR_MESSAGE, apiErrorMessage } from './api-error-message';

function errorWith(status: number, message = 'detalle interno del servidor'): ApiError {
  return { timestamp: '2026-10-22T14:19:20.248Z', status, error: 'x', message, path: '/books' };
}

describe('apiErrorMessage', () => {
  it.each([
    [0, NETWORK_ERROR_MESSAGE],
    [400, 'Los datos enviados no son válidos. Revísalos e inténtalo de nuevo'],
    [404, 'El recurso solicitado no existe'],
    [409, 'Ya existe un registro que entra en conflicto con estos datos'],
    [422, 'El país del proveedor no tiene una moneda asociada: edita el libro y elige otro país'],
    [502, NETWORK_ERROR_MESSAGE],
    [504, NETWORK_ERROR_MESSAGE],
    [503, 'Servicio externo no disponible, intenta de nuevo en un momento'],
    [500, 'Ocurrió un error en el servidor. Inténtalo de nuevo más tarde'],
  ])('status %i -> mensaje en español', (status, expected) => {
    expect(apiErrorMessage(errorWith(status))).toBe(expected);
  });

  it('no filtra el mensaje interno del servidor en un 500', () => {
    expect(apiErrorMessage(errorWith(500, 'NullPointerException en BookService'))).not.toContain(
      'NullPointer',
    );
  });

  it('un status desconocido incluye el código', () => {
    expect(apiErrorMessage(errorWith(418))).toBe('Ocurrió un error inesperado (código 418)');
  });
});
