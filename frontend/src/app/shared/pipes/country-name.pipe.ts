import { Pipe, PipeTransform } from '@angular/core';

const displayNames = new Intl.DisplayNames(['es'], { type: 'region' });

// Código ISO 3166-1 alpha-2 -> nombre del país en español ("ES" -> "España"); si no lo reconoce, el código.
@Pipe({ name: 'countryName' })
export class CountryNamePipe implements PipeTransform {
  transform(code: string | null | undefined): string {
    if (!code) {
      return '';
    }
    try {
      return displayNames.of(code.toUpperCase()) ?? code;
    } catch {
      return code;
    }
  }
}
