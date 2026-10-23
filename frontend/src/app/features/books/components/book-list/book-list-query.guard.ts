import { inject } from '@angular/core';
import { CanActivateFn, RedirectCommand, Router } from '@angular/router';

import { isCanonical, parseBookListQuery, toQueryParams } from './book-list-query';

// Si la URL trae valores inválidos o redundantes, la reemplaza (sin nueva entrada en el historial)
// por su versión saneada; así la barra de direcciones siempre muestra lo que realmente se ve.
export const bookListQueryGuard: CanActivateFn = (route) => {
  const canonical = toQueryParams(parseBookListQuery(route.queryParamMap));
  if (isCanonical(route.queryParamMap, canonical)) {
    return true;
  }
  const router = inject(Router);
  const segments = route.pathFromRoot.flatMap((snapshot) => snapshot.url.map((s) => s.path));
  const target = router.createUrlTree(['/', ...segments], { queryParams: canonical });
  return new RedirectCommand(target, { replaceUrl: true });
};
