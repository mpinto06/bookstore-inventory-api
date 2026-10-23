import { FormControl } from '@angular/forms';

import {
  costError,
  costValidator,
  formatCostInput,
  parseCostInput,
  countryValidator,
  isValidIsbn,
  isbnValidator,
  noNul,
  notBlank,
  stockError,
  stockValidator,
} from './book.validators';

describe('isValidIsbn', () => {
  it.each([
    ['9780306406157', 'ISBN-13'],
    ['978-84-376-0494-7', 'ISBN-13 con guiones'],
    ['978 0 306 40615 7', 'ISBN-13 con espacios'],
    ['0306406152', 'ISBN-10'],
    ['0-306-40615-2', 'ISBN-10 con guiones'],
    ['080442957X', 'ISBN-10 con X final'],
    ['080442957x', 'ISBN-10 con x minúscula'],
  ])('acepta %s (%s)', (isbn) => {
    expect(isValidIsbn(isbn)).toBe(true);
  });

  it.each([
    ['9780306406158', 'dígito verificador ISBN-13 mal'],
    ['0306406153', 'dígito verificador ISBN-10 mal'],
    ['97803064061', 'longitud mal (11)'],
    ['978030640615', 'longitud mal (12)'],
    ['', 'vacío'],
    ['97803064O6157', 'letra en lugar de cero'],
    ['03064061X2', 'X fuera de la última posición'],
    ['978030640615X', 'X en un ISBN-13'],
    ['abcdefghij', 'letras'],
    [`978-0-306-40615-7${' '.repeat(16)}`, 'más de 32 caracteres aunque normalizado sea válido'],
  ])('rechaza %s (%s)', (isbn) => {
    expect(isValidIsbn(isbn)).toBe(false);
  });

  it('el límite de 32 caracteres se aplica antes de normalizar', () => {
    const exactly32 = `${'9780306406157'}${' '.repeat(19)}`;
    expect(exactly32).toHaveLength(32);
    expect(isValidIsbn(exactly32)).toBe(true);
    expect(isValidIsbn(`${exactly32} `)).toBe(false);
  });
});

describe('isbnValidator', () => {
  it('distingue longitud excesiva de ISBN inválido y deja pasar el vacío', () => {
    expect(isbnValidator(new FormControl(''))).toBeNull();
    expect(isbnValidator(new FormControl('9780306406158'))).toEqual({ isbn: true });
    expect(isbnValidator(new FormControl('9'.repeat(33)))).toEqual({ isbnLength: true });
    expect(isbnValidator(new FormControl('978-0-306-40615-7'))).toBeNull();
  });
});

describe('costError', () => {
  it.each([
    [0, 'min'],
    [0.001, 'min'],
    [0.01, null],
    [12.5, null],
    [99999999.99, null],
    [100000000, 'max'],
    [1.234, 'decimals'],
    [10.005, 'decimals'],
    [-5, 'min'],
    [Number.NaN, 'min'],
  ])('%s -> %s', (value, expected) => {
    expect(costError(value)).toBe(expected);
  });
});

describe('costValidator', () => {
  it('traduce el código a un error del control y acepta el vacío (lo cubre required)', () => {
    expect(costValidator(new FormControl(null))).toBeNull();
    expect(costValidator(new FormControl(0.001))).toEqual({ cost_min: true });
    expect(costValidator(new FormControl(100000000))).toEqual({ cost_max: true });
    expect(costValidator(new FormControl(1.234))).toEqual({ cost_decimals: true });
    expect(costValidator(new FormControl(99999999.99))).toBeNull();
  });
});

describe('stockError / stockValidator', () => {
  it.each([
    [0, null],
    [1, null],
    [1_000_000, null],
    [1_000_001, 'max'],
    [-1, 'min'],
    [1.5, 'integer'],
  ])('%s -> %s', (value, expected) => {
    expect(stockError(value)).toBe(expected);
  });

  it('traduce el código a un error del control', () => {
    expect(stockValidator(new FormControl(null))).toBeNull();
    expect(stockValidator(new FormControl(1_000_001))).toEqual({ stock_max: true });
    expect(stockValidator(new FormControl(-1))).toEqual({ stock_min: true });
    expect(stockValidator(new FormControl(2.5))).toEqual({ stock_integer: true });
  });
});

describe('validadores de texto y país', () => {
  it('notBlank rechaza espacios; noNul rechaza el carácter NUL', () => {
    expect(notBlank(new FormControl('   '))).toEqual({ required: true });
    expect(notBlank(new FormControl(' a '))).toBeNull();
    expect(noNul(new FormControl('a\u0000b'))).toEqual({ nul: true });
    expect(noNul(new FormControl('abc'))).toBeNull();
  });

  it('countryValidator acepta solo códigos ISO-2 reales', () => {
    expect(countryValidator(new FormControl('ES'))).toBeNull();
    expect(countryValidator(new FormControl('ve'))).toBeNull();
    expect(countryValidator(new FormControl('ZZ'))).toEqual({ country: true });
    expect(countryValidator(new FormControl('ESP'))).toEqual({ country: true });
  });
});

describe('costo en formato español', () => {
  it.each([
    ['15,99', 15.99],
    ['1.234,56', 1234.56],
    ['1.234', 1234],
    ['42', 42],
    [' 0,5 ', 0.5],
  ])('parsea "%s"', (text, expected) => {
    expect(parseCostInput(text)).toBe(expected);
  });

  it.each(['15.99', '1,2,3', 'abc', '1.23', ',5', '12,'])('rechaza "%s"', (text) => {
    expect(parseCostInput(text)).toBeNaN();
  });

  it('formatea con coma y sin separador de miles', () => {
    expect(formatCostInput(15.99)).toBe('15,99');
    expect(formatCostInput(1234)).toBe('1234');
  });

  it('el validador acepta texto en español y marca formato, mínimo, máximo y decimales', () => {
    expect(costValidator(new FormControl('15,99'))).toBeNull();
    expect(costValidator(new FormControl('15.99'))).toEqual({ cost_format: true });
    expect(costValidator(new FormControl('0,001'))).toEqual({ cost_min: true });
    expect(costValidator(new FormControl('100.000.000'))).toEqual({ cost_max: true });
    expect(costValidator(new FormControl('1,234'))).toEqual({ cost_decimals: true });
  });
});
