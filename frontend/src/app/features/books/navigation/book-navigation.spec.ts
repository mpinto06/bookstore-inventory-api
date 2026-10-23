import { parseBookId, resolveBackUrl } from './book-navigation';

describe('parseBookId', () => {
  it.each([
    ['1', 1],
    ['42', 42],
    ['999999', 999999],
  ])('acepta %s', (raw, expected) => {
    expect(parseBookId(raw)).toBe(expected);
  });

  it.each(['abc', '0', '-1', '1.5', '01', '', ' 1', '1e3', '9'.repeat(30)])('rechaza %j', (raw) => {
    expect(parseBookId(raw)).toBeNull();
  });

  it('rechaza null', () => {
    expect(parseBookId(null)).toBeNull();
  });
});

describe('resolveBackUrl', () => {
  it('usa state.from cuando empieza por /books', () => {
    expect(resolveBackUrl({ from: '/books?page=2&size=50' })).toBe('/books?page=2&size=50');
    expect(resolveBackUrl({ from: '/books/7' })).toBe('/books/7');
  });

  it.each([
    undefined,
    null,
    {},
    { from: 42 },
    { from: '/otra-ruta' },
    { from: '/booksfoo' },
    { from: '/books//evil.example' },
    { from: 'https://evil.example/books' },
  ])('vuelve al listado con %j', (state) => {
    expect(resolveBackUrl(state)).toBe('/books');
  });
});
