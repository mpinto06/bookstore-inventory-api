import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiError } from '../../shared/models/api-error.model';
import { NETWORK_ERROR_MESSAGE, errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // Lanza GET /api/books y devuelve una función que lee el error recibido por el consumidor.
  function requestBooks(): () => unknown {
    let received: unknown;
    client.get('/api/books').subscribe({ error: (error: unknown) => (received = error) });
    return () => received;
  }

  it('respeta el ApiError que envía el backend', () => {
    const backendError: ApiError = {
      timestamp: '2026-10-22T14:19:20.248Z',
      status: 400,
      error: 'Bad Request',
      message: 'La petición contiene datos inválidos',
      path: '/books',
      field_errors: [{ field: 'size', message: 'size debe estar entre 1 y 100' }],
    };
    const error = requestBooks();

    http.expectOne('/api/books').flush(backendError, { status: 400, statusText: 'Bad Request' });

    expect(error()).toEqual(backendError);
    expect(error()).not.toBeInstanceOf(HttpErrorResponse);
  });

  it('convierte un status 0 (sin red o backend caído) en "No se pudo conectar con el servidor"', () => {
    const error = requestBooks();

    http.expectOne('/api/books').error(new ProgressEvent('error'), { status: 0, statusText: '' });

    expect(error()).toMatchObject({
      status: 0,
      message: NETWORK_ERROR_MESSAGE,
      path: '/api/books',
    });
  });

  it('usa un mensaje genérico según el status si el cuerpo no es JSON (502 de un proxy)', () => {
    const error = requestBooks();

    http
      .expectOne('/api/books')
      .flush('<html><body>Bad Gateway</body></html>', { status: 502, statusText: 'Bad Gateway' });

    expect(error()).toMatchObject({
      status: 502,
      error: 'Bad Gateway',
      message: 'El servidor no respondió correctamente',
      path: '/api/books',
    });
    expect(typeof (error() as ApiError).timestamp).toBe('string');
  });

  it('da un mensaje claro de servicio no disponible para un 503 sin ApiError', () => {
    const error = requestBooks();

    http.expectOne('/api/books').flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(error()).toMatchObject({
      status: 503,
      message: 'El servicio no está disponible en este momento. Inténtalo de nuevo más tarde',
    });
  });

  it('conserva el mensaje del backend en un 503 con ApiError', () => {
    const error = requestBooks();

    http.expectOne('/api/books').flush(
      {
        timestamp: '2026-10-22T14:19:20.248Z',
        status: 503,
        error: 'Service Unavailable',
        message: 'La base de datos no está disponible',
        path: '/books',
      },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(error()).toMatchObject({ status: 503, message: 'La base de datos no está disponible' });
  });

  it('no altera las respuestas correctas', () => {
    let body: unknown;
    client.get('/api/books/categories').subscribe((response) => (body = response));

    http.expectOne('/api/books/categories').flush(['Ciencia']);

    expect(body).toEqual(['Ciencia']);
  });
});
