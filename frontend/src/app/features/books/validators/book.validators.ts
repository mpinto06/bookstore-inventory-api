import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { COUNTRY_CODES } from '../models/country-options';

// Límites idénticos a los de BookRequest y sus validadores en el backend.
export const TEXT_MAX_LENGTH = 255;
export const CATEGORY_MAX_LENGTH = 100;
export const ISBN_MAX_RAW_LENGTH = 32;
export const COST_MIN = 0.01;
export const COST_MAX_INTEGER_DIGITS = 8;
export const COST_MAX_DECIMALS = 2;
export const STOCK_MIN = 0;
export const STOCK_MAX = 1_000_000;

// Igual que IsbnValidator.normalize: quita guiones y espacios y pasa a mayúsculas.
export function normalizeIsbn(raw: string): string {
  return raw.replaceAll('-', '').replaceAll(' ', '').toUpperCase();
}

// Misma lógica que IsbnValidator.isValid del backend (ISBN-10 con X final, ISBN-13, dígito verificador real).
export function isValidIsbn(raw: string): boolean {
  if (raw.length > ISBN_MAX_RAW_LENGTH) {
    return false;
  }
  const isbn = normalizeIsbn(raw);
  switch (isbn.length) {
    case 10:
      return isValidIsbn10(isbn);
    case 13:
      return isValidIsbn13(isbn);
    default:
      return false;
  }
}

function isValidIsbn10(isbn: string): boolean {
  let sum = 0;
  for (let i = 0; i < 10; i++) {
    const char = isbn[i];
    let value: number;
    if (char >= '0' && char <= '9') {
      value = Number(char);
    } else if (char === 'X' && i === 9) {
      value = 10;
    } else {
      return false;
    }
    sum += value * (10 - i);
  }
  return sum % 11 === 0;
}

function isValidIsbn13(isbn: string): boolean {
  let sum = 0;
  for (let i = 0; i < 13; i++) {
    const char = isbn[i];
    if (char < '0' || char > '9') {
      return false;
    }
    sum += Number(char) * (i % 2 === 0 ? 1 : 3);
  }
  return sum % 10 === 0;
}

// Devuelve el código de error del costo, o null si es válido (0.01 .. 99999999.99, máx. 2 decimales).
export function costError(value: number): 'min' | 'max' | 'decimals' | null {
  if (!Number.isFinite(value) || value < COST_MIN) {
    return 'min';
  }
  const text = String(value);
  // Notación científica solo aparece con valores < 1e-6 o >= 1e21: ya fuera de rango.
  if (text.includes('e')) {
    return 'max';
  }
  const [integer, decimals = ''] = text.split('.');
  if (integer.length > COST_MAX_INTEGER_DIGITS) {
    return 'max';
  }
  return decimals.length > COST_MAX_DECIMALS ? 'decimals' : null;
}

// Devuelve el código de error del stock, o null si es válido (entero 0 .. 1.000.000).
export function stockError(value: number): 'integer' | 'min' | 'max' | null {
  if (!Number.isInteger(value)) {
    return 'integer';
  }
  if (value < STOCK_MIN) {
    return 'min';
  }
  return value > STOCK_MAX ? 'max' : null;
}

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

// @NotBlank: además de vacío rechaza cadenas solo con espacios.
export const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim() !== '' ? null : { required: true };

// @NoNul: PostgreSQL no admite el carácter NUL en texto.
export const noNul: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.includes('\u0000') ? { nul: true } : null;

export const isbnValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  if (typeof value !== 'string' || value.trim() === '') {
    return null;
  }
  if (value.length > ISBN_MAX_RAW_LENGTH) {
    return { isbnLength: true };
  }
  return isValidIsbn(value) ? null : { isbn: true };
};

export const costValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (isEmpty(control.value)) {
    return null;
  }
  const code = costError(Number(control.value));
  return code ? { [`cost_${code}`]: true } : null;
};

export const stockValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (isEmpty(control.value)) {
    return null;
  }
  const code = stockError(Number(control.value));
  return code ? { [`stock_${code}`]: true } : null;
};

export const countryValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = control.value;
  if (typeof value !== 'string' || value === '') {
    return null;
  }
  return COUNTRY_CODES.has(value.toUpperCase()) ? null : { country: true };
};
