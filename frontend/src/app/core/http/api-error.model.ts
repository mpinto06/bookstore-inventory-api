// Formato único de error del backend (snake_case, tal como llega en el JSON).
export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  timestamp: string;
  // 0 cuando no hubo respuesta del servidor (sin red o backend caído).
  status: number;
  error: string;
  message: string;
  path: string;
  field_errors?: FieldError[];
}
