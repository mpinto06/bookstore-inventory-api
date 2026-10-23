# bookstore-inventory-api

API REST de inventario de librerías con una SPA en Angular que la consume.

## Requisitos

- Java 21
- Docker (con Docker Compose) para la base de datos PostgreSQL 18
- Node.js 24 LTS (o 22 LTS ≥ 22.22.3) y npm para el frontend (ver [Frontend](#frontend))

## Instalación y ejecución

1. (Opcional) copia `.env.example` a `.env` para cambiar credenciales o puerto de la BD.
   Los valores por defecto coinciden con los de `backend/src/main/resources/application.properties`.
   La BD se publica en el puerto **5433** del host (no en el 5432 estándar) para evitar
   conflictos con una posible instancia local de PostgreSQL. Si cambias `DB_PORT`, define
   también `DB_URL` al arrancar el backend.
2. Levanta la base de datos desde la raíz del repo:

   ```bash
   docker compose up -d --wait
   ```

3. Arranca el backend:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   Flyway aplica las migraciones de `backend/src/main/resources/db/migration` al arrancar.

4. Tests: ver la sección [Tests](#tests) (los de integración usan Docker).

### Reiniciar la base de datos desde cero

```bash
docker compose down -v
docker compose up -d --wait
```

`-v` elimina el volumen con los datos. Es necesario porque, mientras el proyecto está en
desarrollo, las migraciones se **editan** en lugar de apilar `ALTER`s (V2, V3...) para mantener
un historial limpio; Flyway detectaría el cambio de checksum en una migración ya aplicada, así
que hay que recrear la BD para re-aplicarla.

## Tests

```bash
cd backend
./mvnw clean verify      # o ./mvnw test
```

Los tests de integración levantan un **PostgreSQL 18 real con Testcontainers**, por lo que **requieren
Docker en ejecución** (no necesitan `docker compose up`: arrancan su propio contenedor, uno solo para toda
la ejecución, que se destruye al terminar). Ningún test usa internet: el proveedor de tasas se sustituye por
un doble y el refresco (`exchange-rate.refresh.enabled=false`) está desactivado.

| Grupo | Clases | Qué cubre |
|-------|--------|-----------|
| Unitarios (sin Spring) | `PriceServiceTest` | fórmula de precio: ejemplo 15.99 x 0.85 -> 13.59 / 19.03, redondeo HALF_UP en el límite, costo máximo con tasa alta, margen configurable, 2 decimales, moneda local por país (ES, VE, AQ) |
| | `ExchangeRateServiceTest` | `fetchLatest` descarta tasas inválidas; `getRate` con reloj controlado: caché vigente/vencido, respaldo, pausa tras fallo y reintento, moneda sin tasa, 50 hilos -> 1 sola llamada |
| | `RestClientExchangeRateProviderTest` | cliente HTTP contra un servidor local: respuesta válida, respuestas inválidas, status 500, timeout |
| | `ExchangeRateRefresherTest`, `ExchangeRateCronTest` | el refresco nunca propaga fallos y respeta `on-startup`; el cron por defecto equivale a las 03:00 de Caracas (07:00 UTC) |
| | `IsbnValidatorTest`, `CountryCodeValidatorTest`, `NoNulValidatorTest`, `BookPagingTest`, `GlobalExceptionHandlerTest` | validadores, paginación y manejo de errores |
| Integración (MockMvc + Postgres) | `BookCrudIntegrationTest` | alta, 409 de ISBN (con otros guiones), validaciones 400, campos ignorados, ids inválidos, 404, paginación y orden, PUT, DELETE, forma de `ApiError` |
| | `BookQueryIntegrationTest` | search (exacta, insensible a mayúsculas), low-stock (umbral inclusivo y orden), categories |
| | `CalculatePriceIntegrationTest` | LIVE, FALLBACK, 503, 422, 404, 400, PUT que borra el precio, `updated_at` al recalcular, `exchange_rates` intacta |
| | `DatabaseIntegrationTest` | trigger de `updated_at`, CHECK de `books` y `exchange_rates`, ISBN único, `NUMERIC(20,2)` |

## Frontend

SPA en **Angular 22.2** (standalone, signals, zoneless) en `frontend/`. Interfaz en español.

**Requisitos:** Node.js `^22.22.3 || ^24.15.0` (desarrollado con Node 24.16.0 LTS, npm 11) — es el rango
que exige Angular 22. Las versiones de `package.json` están fijadas (sin `^` ni `~`).

```bash
cd frontend
npm ci                          # instala exactamente lo de package-lock.json
npx ng serve                    # http://localhost:4200 (requiere el backend en :8080)
npx ng build                    # build de producción en frontend/dist/
npx ng test --watch=false       # tests unitarios (Vitest + jsdom), sin backend
```

**Proxy `/api`:** la SPA siempre llama a `/api/...` (`environment.apiBaseUrl = '/api'`). En desarrollo,
`ng serve` reenvía esas peticiones a `http://localhost:8080` quitando el prefijo (`proxy.conf.json`:
`/api/books` -> `/books`). En producción la misma regla la aplicará nginx. Para el navegador todo es el
mismo origen, así que el backend no necesita CORS. Con el backend caído, el proxy responde **502**: la
SPA lo muestra como "Sin conexión con el servidor" con un botón "Reintentar".

**Pantalla de libros (`/books`):** listado paginado (10/20/50/100 por página), orden por los encabezados
de la lista blanca (título, categoría, stock, costo), filtro por categoría (desplegable con
`/books/categories`) y por stock bajo (umbral 0..1000000). Todo el estado vive en la URL, p. ej.
`/books?page=1&size=50&sort=cost_usd,desc&filter=category&category=Historia`, así que recargar o
compartir el enlace conserva la vista.

### Estructura

```
frontend/
├── proxy.conf.json                 /api -> http://localhost:8080 (solo ng serve)
├── public/                         estáticos (favicon)
└── src/
    ├── environments/               apiBaseUrl = '/api'
    ├── styles/                     _variables.scss (tokens CSS), _mixins.scss, styles.scss (entrada)
    └── app/
        ├── app.component.ts, app.config.ts, app.routes.ts
        ├── core/http/              error.interceptor.ts (HttpErrorResponse -> ApiError)
        ├── shared/
        │   ├── components/         alert, empty-state, pagination, spinner, status-badge
        │   ├── models/             ApiError, FieldError, PageResponse<T> (solo interfaces)
        │   └── pipes/              countryName (ISO-2 -> nombre del país)
        ├── layout/                 header, footer, main-layout (<router-outlet>)
        └── features/books/
            ├── books.routes.ts     cargado con loadChildren
            ├── models/             Book, lista blanca de sort, query del listado, constantes
            ├── services/           book.service.ts (HttpClient)
            └── components/book-list/  componente smart + saneado de la URL y su guard
```

## Tasas de cambio

El backend guarda tasas de cambio con base USD en la tabla `exchange_rates` (moneda, tasa, fecha de
la tasa y momento de la consulta). Nunca hay tasas escritas a mano en el código.

**Cómo se mantienen actualizadas:**

1. **Seed:** la migración `V2` incluye un snapshot real y fechado (todas las monedas que devuelve la
   API, descargado el 2026-10-22). Permite operar aunque la API externa no responda al arrancar.
2. **Refresco al arrancar:** al terminar el arranque (después de Flyway) se piden las tasas a la API
   y se sobrescriben con un upsert. Si falla, se registra un `WARN` y la aplicación arranca igual con
   las tasas que ya hay.
3. **Refresco diario:** un job programado repite el refresco todos los días a las **03:00
   `America/Caracas`** (07:00 UTC). Si falla, `WARN` y se conservan las tasas guardadas; el siguiente
   disparo lo reintenta.

Fuente: `https://api.exchangerate-api.com/v4/latest/USD`. Un fallo de la API (red, timeout, status
distinto de 2xx, JSON ilegible, base distinta de USD, `rates` vacío o fecha inválida) nunca tumba la
aplicación ni deja datos a medias: el refresco es atómico. Las tasas individuales no positivas, mayores
a 1.000.000.000 o con moneda que no sea de 3 letras mayúsculas se descartan sin afectar a las demás.

**Configuración** (propiedad de `application.properties` <- variable de entorno, con su valor por defecto):

| Propiedad | Variable de entorno | Por defecto |
|-----------|---------------------|-------------|
| `exchange-rate.api.url` | `EXCHANGE_RATE_API_URL` | `https://api.exchangerate-api.com/v4/latest/USD` |
| `exchange-rate.api.connect-timeout` | `EXCHANGE_RATE_CONNECT_TIMEOUT` | `2s` |
| `exchange-rate.api.read-timeout` | `EXCHANGE_RATE_READ_TIMEOUT` | `3s` |
| `exchange-rate.refresh.enabled` | `EXCHANGE_RATE_REFRESH_ENABLED` | `true` (`false` desactiva el refresco al arrancar y el programado; los tests lo usan) |
| `exchange-rate.refresh.on-startup` | `EXCHANGE_RATE_REFRESH_ON_STARTUP` | `true` |
| `exchange-rate.refresh.cron` | `EXCHANGE_RATE_REFRESH_CRON` | `0 0 3 * * *` |
| `exchange-rate.refresh.zone` | `EXCHANGE_RATE_REFRESH_ZONE` | `America/Caracas` |
| `exchange-rate.cache-ttl` | `EXCHANGE_RATE_CACHE_TTL` | `PT15M` (vigencia de la caché en memoria al calcular precios) |
| `exchange-rate.failure-backoff` | `EXCHANGE_RATE_FAILURE_BACKOFF` | `PT1M` (pausa sin llamar a la API tras un fallo) |
| `pricing.margin-percentage` | `PRICING_MARGIN_PERCENTAGE` | `40` (margen sobre el costo en moneda local, en %) |

Ejemplo: `EXCHANGE_RATE_REFRESH_CRON="0 * * * * *" ./mvnw spring-boot:run` refresca cada minuto.

## Documentación de la API

Con la aplicación en marcha (`./mvnw spring-boot:run`):

| Recurso | URL |
|---------|-----|
| Swagger UI (probar endpoints desde el navegador) | http://localhost:8080/swagger-ui.html |
| Especificación OpenAPI 3.1 (JSON) | http://localhost:8080/v3/api-docs |

La especificación se puede **importar en Postman** (*Import* -> *Link* con la URL de `/v3/api-docs`, o
guardando el JSON como archivo) para obtener una colección con todos los endpoints.

**Desactivarlas por configuración** (por defecto ambas están activas): `springdoc.swagger-ui.enabled` y
`springdoc.api-docs.enabled` en `application.properties`, o las variables de entorno
`SPRINGDOC_SWAGGER_UI_ENABLED` y `SPRINGDOC_API_DOCS_ENABLED`. Con la primera en `false`,
`/swagger-ui.html` responde 404 y `/v3/api-docs` sigue disponible; con la segunda en `false` no hay
especificación y, como la UI depende de ella, tampoco hay UI. La API de negocio no se ve afectada.

```bash
SPRINGDOC_SWAGGER_UI_ENABLED=false ./mvnw spring-boot:run
```

## Endpoints

Base URL: `http://localhost:8080`. El JSON usa `snake_case` en peticiones y respuestas.

### Libros (`/books`)

| Método | Ruta | Respuesta |
|--------|------|-----------|
| POST | `/books` | 201 + `Location` |
| GET | `/books` | 200 (paginado) |
| GET | `/books/{id}` | 200 / 404 |
| PUT | `/books/{id}` | 200 / 404 / 409 |
| DELETE | `/books/{id}` | 204 / 404 |
| GET | `/books/search?category=` | 200 (paginado) / 400 |
| GET | `/books/low-stock` | 200 (paginado) / 400 |
| GET | `/books/categories` | 200 (arreglo) |

Campos del cuerpo de POST/PUT (`id`, `selling_price_local`, `selling_currency`, `created_at` y
`updated_at` los gestiona el servidor y se ignoran si llegan):

| Campo | Regla |
|-------|-------|
| `title`, `author` | obligatorio, máx. 255 caracteres (se recortan espacios) |
| `category` | obligatorio, máx. 100 caracteres (se recortan espacios) |
| `isbn` | ISBN-10 o ISBN-13 válido (dígito verificador), con o sin guiones, máx. 32 caracteres; se guarda sin guiones |
| `cost_usd` | obligatorio, ≥ 0.01, máx. 8 dígitos enteros y 2 decimales |
| `stock_quantity` | obligatorio, entero entre 0 y 1000000 |
| `supplier_country` | código ISO 3166-1 alpha-2 real (`es` se guarda como `ES`) |

**Crear**

```bash
curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"Don Quijote","author":"Cervantes","isbn":"978-84-376-0494-7",
       "cost_usd":12.50,"stock_quantity":10,"category":"Novela","supplier_country":"es"}'
# 201 Created   Location: http://localhost:8080/books/1
```

**Obtener uno / listar / eliminar**

```bash
curl http://localhost:8080/books/1
curl "http://localhost:8080/books?page=0&size=20&sort=cost_usd,desc"
curl -X DELETE http://localhost:8080/books/1        # 204
```

**Reemplazar (PUT, reemplazo completo: hay que enviar todos los campos)**

```bash
curl -X PUT http://localhost:8080/books/1 -H 'Content-Type: application/json' \
  -d '{"title":"Don Quijote","author":"Cervantes","isbn":"9788437604947",
       "cost_usd":15.00,"stock_quantity":8,"category":"Novela","supplier_country":"ES"}'
```

Si el PUT cambia `cost_usd` o `supplier_country`, `selling_price_local` y `selling_currency` se
ponen en `null`: el precio calculado quedó obsoleto y debe recalcularse.

### Paginación de `GET /books`

| Parámetro | Regla | Por defecto |
|-----------|-------|-------------|
| `page` | entero ≥ 0 (y `page * size` ≤ 2147483647) | `0` |
| `size` | entero entre 1 y 100 | `20` |
| `sort` | `campo,asc\|desc` (la dirección es opcional, `asc` por defecto) | `id,asc` |

Campos permitidos en `sort`: `id`, `title`, `author`, `category`, `cost_usd`, `stock_quantity`,
`created_at`. Cualquier otro valor, o un `page`/`size` inválido, responde 400. Siempre se añade
`id` como desempate para que las páginas sean estables.

```json
{ "content": [ { "id": 1, "...": "..." } ], "page": 0, "size": 20, "total_elements": 1, "total_pages": 1 }
```

### Búsqueda, stock bajo y categorías

Las rutas literales `/books/search`, `/books/low-stock` y `/books/categories` tienen prioridad sobre
`/books/{id}`. Los dos primeros usan la misma paginación (`page`, `size`, `sort`) y los mismos
límites y errores 400 que `GET /books`.

**Buscar por categoría** (`category` obligatorio, máx. 100 caracteres, se recortan los espacios;
coincidencia exacta sin distinguir mayúsculas; orden por defecto `id,asc`):

```bash
curl "http://localhost:8080/books/search?category=literatura%20cl%C3%A1sica&sort=cost_usd,desc"
```

Una categoría sin resultados responde 200 con `content` vacío. Sin `category`, vacío, solo espacios
o de más de 100 caracteres responde 400.

**Stock bajo** (`threshold` opcional, entero entre 0 y 1000000, por defecto 10; devuelve los libros
con `stock_quantity <= threshold`; orden por defecto `stock_quantity,asc` y luego `id,asc`):

```bash
curl "http://localhost:8080/books/low-stock?threshold=5&page=0&size=20"
```

Un `threshold` negativo, mayor que 1000000, no numérico o desbordado responde 400.

**Categorías existentes** (sin paginación, distintas y en orden alfabético; `[]` si no hay libros):

```bash
curl http://localhost:8080/books/categories
# ["Ciencia","Historia","Literatura Clásica"]
```

### Cálculo de precio (`POST /books/{id}/calculate-price`)

Sin body. Convierte `cost_usd` a la moneda del país del proveedor, aplica el margen, guarda
`selling_price_local` y `selling_currency` en el libro (cambia `updated_at`) y devuelve el desglose.

```bash
curl -X POST http://localhost:8080/books/1/calculate-price
```

```json
{
  "book_id": 1,
  "cost_usd": 15.99,
  "exchange_rate": 0.85,
  "cost_local": 13.59,
  "margin_percentage": 40,
  "selling_price_local": 19.03,
  "currency": "EUR",
  "calculation_timestamp": "2026-10-23T00:57:15.967682500Z",
  "rate_source": "LIVE",
  "rate_date": "2026-10-23"
}
```

- `cost_local = round2(cost_usd * exchange_rate)` y `selling_price_local = round2(cost_local * (1 + margen/100))`
  (HALF_UP, 2 decimales).
- `rate_source`: `LIVE` (datos de la API de los últimos 15 minutos) o `FALLBACK` (tasa guardada en la BD
  porque la API no respondió); `rate_date` es la fecha de esa tasa. Son campos adicionales.
- Errores: 400 (`id` inválido), 404 (libro inexistente), 409 (el libro cambió mientras se calculaba),
  422 (el país del proveedor no tiene moneda, p. ej. `AQ`), 503 (sin tasa disponible para la moneda
  ni en la API ni en la BD, o BD caída).

### Formato de error

Todos los errores (incluidos 404 de rutas, 405 y 415) tienen el mismo formato; `field_errors`
solo aparece en errores de validación:

```json
{
  "timestamp": "2026-10-22T14:19:20.248Z",
  "status": 400,
  "error": "Bad Request",
  "message": "La petición contiene datos inválidos",
  "path": "/books",
  "field_errors": [ { "field": "cost_usd", "message": "cost_usd debe ser mayor o igual a 0.01" } ]
}
```

| Status | Cuándo |
|--------|--------|
| 400 | validación del body, JSON malformado o con tipos/rangos incorrectos, `id`/`page`/`size`/`sort` inválidos |
| 404 | libro o ruta inexistente |
| 405 / 415 | método HTTP o tipo de contenido no soportado |
| 409 | ISBN duplicado; libro modificado durante `calculate-price` |
| 422 | país del proveedor sin moneda asociada |
| 503 | base de datos no disponible; sin tasa de cambio para la moneda |
| 500 | fallo inesperado del servidor (mensaje genérico; el detalle solo va al log) |

## Decisiones técnicas

- **ISBN normalizado y validado con dígito verificador:** se acepta ISBN-10 o ISBN-13 con o sin
  guiones/espacios (máx. 32 caracteres de entrada), se guarda normalizado (sin guiones ni
  espacios, `VARCHAR(13)`, con índice único) y se valida el dígito verificador real de cada
  formato, no solo el formato.
- **País validado contra ISO 3166-1:** `supplier_country` se valida con `Locale.getISOCountries()`,
  de modo que "XX" o "12" se rechazan aunque tengan dos caracteres.
- **Precio siempre con su moneda:** `selling_price_local` y `selling_currency` son ambos nulos o
  ambos presentes (CHECK en la BD); un precio nunca se guarda sin moneda.
- **`BigDecimal` para dinero:** evita errores de redondeo de `double`; columnas `NUMERIC` con
  precisión y escala explícitas.
- **Restricciones en la BD y en la aplicación:** la BD es la última línea de defensa de la
  integridad (CHECK, NOT NULL, UNIQUE, longitudes), y la aplicación valida lo mismo con límites
  explícitos de longitud y rango para que un dato inválido produzca un error 4xx claro y nunca
  un error 500 por violar una restricción.
- **409 para ISBN duplicado:** el ISBN se comprueba antes de guardar (`existsByIsbn`, excluyendo el
  propio libro en el PUT) y, además, el índice único de la BD protege la condición de carrera entre
  dos peticiones simultáneas: esa violación también se responde 409, no 500.
- **PUT como reemplazo completo:** exige todos los campos y sustituye el recurso entero (no hay PATCH).
- **Reseteo del precio de venta:** si un PUT cambia `cost_usd` o `supplier_country`, el precio
  calculado (`selling_price_local` + `selling_currency`) se pone en `null` porque quedó obsoleto;
  nunca se conserva un precio calculado con datos que ya no son los del libro.
- **Lista blanca de ordenamiento:** `sort` solo admite campos conocidos en snake_case, traducidos
  a propiedades de la entidad; así un nombre arbitrario del cliente no llega a Spring Data
  (donde provocaría un 500). `page` y `size` también están acotados.
- **DTO propio de paginación:** `PageResponse` en lugar de serializar `Page` de Spring Data, para
  que el contrato JSON no dependa de una clase interna del framework.
- **Ninguna entrada inválida produce un 500:** los límites de cada campo coinciden con las
  columnas de la BD (longitudes, rangos, sin carácter NUL), y un `@RestControllerAdvice` convierte
  cada error en un 4xx/503 con formato único; el 500 queda para fallos realmente inesperados.
- **Búsqueda exacta e insensible a mayúsculas:** `category` se compara completa (no por fragmento)
  ignorando mayúsculas/minúsculas, de modo que "literatura clásica" encuentra "Literatura Clásica".
  No es insensible a acentos y no usa extensiones de PostgreSQL.
- **Categoría inexistente -> 200 con página vacía:** buscar sin resultados no es un error; un 404
  se reserva para recursos concretos que no existen.
- **Stock bajo con `<=`:** un libro tiene stock bajo si `stock_quantity <= threshold`, así que el
  umbral es inclusivo (con el umbral por defecto de 10, un libro con 10 unidades entra).
- **Límite del umbral alineado con `stock_quantity`:** `threshold` admite 0..1000000, el mismo
  rango que el stock; un valor mayor nunca podría filtrar nada distinto y se rechaza con 400.
- **`/books/categories`:** alimenta el desplegable de filtros del frontend con las categorías que
  realmente existen, para que el usuario no tenga que escribirlas (y evitar errores tipográficos
  en `/books/search`).
- **Snapshot real fechado en V2 en lugar de tasas fijas en el código:** la tabla nace con tasas reales
  (generadas con un script desde la API, sin redondear ni inventar valores) y un comentario con la fecha
  de la descarga. Es solo un punto de partida: el primer refresco exitoso lo sobrescribe. Así la
  aplicación funciona aunque la API esté caída en el primer arranque, sin código con tasas inventadas.
- **Zona horaria explícita en el cron:** `0 0 3 * * *` se evalúa en `America/Caracas` (`-04:00`, 07:00
  UTC) y no en la zona del servidor, porque en Docker el reloj suele estar en UTC y el job correría a
  otra hora local.
- **Upsert idempotente por lotes:** `INSERT ... ON CONFLICT (currency) DO UPDATE` con `batchUpdate` en una
  sola transacción. Repetir el refresco (arranque + cron, reintentos) deja siempre el mismo estado y un
  fallo a medias no deja tasas parcialmente actualizadas.
- **Una sola instancia:** el job programado asume una única instancia del backend. Como el upsert es
  idempotente, varias instancias refrescando a la vez no corromperían nada (solo harían llamadas
  redundantes a la API), por lo que no se añade ningún bloqueo distribuido (p. ej. ShedLock).
- **Un fallo de la API externa nunca tumba la aplicación:** timeouts de conexión (2 s) y lectura (3 s),
  cualquier excepción del refresco se captura y se registra como `WARN` (nunca 500 ni fallo de arranque)
  y se siguen usando las últimas tasas guardadas.
- **API v4 gratuita:** la respuesta de `v4/latest/USD` incluye `WARNING_UPGRADE_TO_V6`, que avisa de que
  existe una v6 (con API key). Se usa la v4 porque no requiere clave; el proveedor está detrás de la
  interfaz `ExchangeRateProvider`, así que migrar a la v6 solo afecta a una clase.
- **`selling_price_local` como `NUMERIC(20,2)`:** con `(14,2)` un costo alto convertido a una moneda de
  tasa muy alta desbordaría la columna y daría un 500.
- **Moneda local derivada del país del proveedor:** `Currency.getInstance(Locale.of("", pais))` del JDK, sin
  tabla de países en el código (ES -> EUR, VE -> VES, CL -> CLP). Un país sin moneda (p. ej. `AQ`) responde
  422 con un mensaje claro.
- **Cadena de tasas: caché en memoria -> API -> tabla de respaldo -> 503:** `getRate` usa primero el último
  resultado de la API guardado en memoria (15 min), después consulta la API, si falla usa la última tasa
  guardada en `exchange_rates` (de cualquier antigüedad) y solo si la moneda tampoco está ahí responde 503.
- **`exchange_rates` solo se escribe al arrancar y a las 3 AM:** el proveedor actualiza las tasas una vez al
  día, así que `calculate-price` nunca escribe en esa tabla (solo lee el respaldo).
- **Caché de 15 min, un solo refresco a la vez y pausa de 60 s tras un fallo:** ante caché vencido, un
  único hilo llama a la API (bajo un bloqueo; los demás reutilizan el resultado) y, si falla, durante 60 s
  no se vuelve a llamar y se responde con el respaldo de inmediato. Así 50 peticiones simultáneas generan una
  sola llamada y una API caída no añade latencia (3 s de timeout) a cada petición. Es estado en memoria, sin
  librerías externas (válido para una sola instancia).
- **`rate_source`:** `LIVE` = datos de la API de los últimos 15 minutos; `FALLBACK` = tasa guardada porque la
  API no respondió (o no traía la moneda). Con `FALLBACK`, `rate_date` indica qué tan vieja es.
- **Redondeo HALF_UP y cálculo sobre `cost_local` redondeado:** el precio se calcula a partir del costo local
  ya redondeado para que el desglose cuadre (15.99 x 0.85 = 13.59 -> 19.03).
- **Margen configurable:** `pricing.margin-percentage` (40 por defecto), no está fijo en el código.
- **`NUMERIC(20,2)` para el precio:** un costo máximo (99.999.999,99 USD) en una moneda de tasa alta no
  desborda la columna.
- **Sin transacción abierta durante la llamada externa:** `calculate-price` lee el libro, obtiene la tasa
  (posible llamada HTTP) sin transacción y solo después abre una transacción corta que recarga el libro y
  guarda el precio. Si el libro cambió de costo o país entretanto responde 409 en lugar de guardar un precio
  obsoleto; si fue eliminado, 404.
- **Limitación del VES:** la respuesta de la API no indica si la tasa del bolívar es la oficial o la paralela;
  se usa tal cual la devuelve el proveedor.
- **Testcontainers con PostgreSQL real y no H2:** el esquema depende de características de PostgreSQL (el
  trigger que mantiene `updated_at`, los `CHECK`, los tipos `NUMERIC(20,2)`/`NUMERIC(20,10)` y el
  `INSERT ... ON CONFLICT`). H2 las emularía de forma distinta y los tests pasarían aunque fallaran en
  producción. Además Flyway aplica las migraciones reales y Hibernate valida el esquema en cada ejecución.
- **`java.time.Clock` inyectado en `ExchangeRateService`:** el TTL de la caché y la pausa tras un fallo
  dependen del tiempo; con un `Clock` que el test avanza a voluntad se prueban sin `Thread.sleep` ni
  depender del reloj real. En producción el bean es `Clock.systemUTC()`.
- **springdoc 3.x por compatibilidad con Spring Boot 4:** se usa `springdoc-openapi-starter-webmvc-ui` 3.1.1,
  la línea que soporta Boot 4 (la 2.x es para Boot 3). La documentación se escribe con anotaciones de
  `io.swagger.v3.oas.annotations`; las de los endpoints viven en la interfaz `BookApi` (que implementa
  `BookController`) para que el controlador siga legible. Los errores genéricos 400/500/503 se añaden a todas
  las operaciones desde un `OpenApiCustomizer`, y cada endpoint solo declara los específicos (404, 409, 422).
- **Esquemas en `snake_case` verificados:** springdoc 3.1.1 genera los esquemas con su propio `ObjectMapper`
  (Jackson 2, vía swagger-core), que no hereda `spring.jackson.property-naming-strategy` (Jackson 3), así que sin
  más los esquemas salían en camelCase (`costUsd`) aunque el JSON real use `cost_usd`. Un bean `ModelResolver` con
  `SNAKE_CASE` lo corrige y `OpenApiIntegrationTest` lo verifica contra `/v3/api-docs`.
- **Constraints propias descritas con `@Schema`:** springdoc deduce solo las constraints estándar de Bean
  Validation (`@Size`, `@Min`, `@Max`, `@DecimalMin`, `@NotBlank`), pero no `@ValidIsbn` ni `@ValidCountryCode`,
  que son validadores propios; por eso su regla (ISBN-10/13 con dígito verificador, país ISO 3166-1 alpha-2) se
  describe a mano en la `description` del campo. Los límites de `page`, `size` y `threshold` se validan en código
  (no con anotaciones sobre el parámetro), así que también se declaran a mano con `@Parameter`/`@Schema`.

### Frontend

- **Arquitectura por capas (`core` / `shared` / `layout` / `features`):** `core` contiene los singletons
  globales (el interceptor HTTP) y nunca se importa desde `features` ni `shared`; `shared` solo tiene piezas
  presentacionales (componentes *dumb* con `input()`/`output()`, un pipe y modelos que son solo interfaces),
  sin servicios ni lógica de negocio; `layout` es el armazón (cabecera, pie y `router-outlet`); cada
  `feature` agrupa sus componentes *smart*, servicios, modelos y rutas, y se carga de forma diferida
  (`loadChildren`). Las dependencias van en un solo sentido, así que una feature se puede quitar sin tocar
  el resto.
- **Angular moderno sin APIs experimentales:** standalone, `OnPush` en todos los componentes, signals para el
  estado, control flow (`@if`/`@for`/`@switch`), `inject()`, formularios reactivos tipados y zoneless (el
  valor por defecto del CLI). El HTTP usa `HttpClient` + RxJS (no los recursos HTTP experimentales). Los
  schematics de `angular.json` generan nombres con sufijo de tipo (`*.component.ts`, `*.service.ts`).
- **La URL como fuente única de verdad:** página, tamaño, orden y filtros son query params. El componente los
  lee, los sanea (valor inválido -> valor por defecto: página negativa -> 0, tamaño fuera de 10/20/50/100 ->
  20, sort fuera de la lista blanca -> orden por defecto, umbral fuera de 0..1000000 -> 10) y pide los datos
  con `switchMap` (cancela la petición anterior). Así nunca se envía al backend una petición que se sabe
  inválida, y recargar, volver atrás o compartir el enlace muestra exactamente la misma vista. Un guard
  reemplaza la URL inválida por la saneada sin crear una entrada nueva en el historial.
- **Modelos en `snake_case`, sin capa de mapeo:** las interfaces (`Book`, `PageResponse<T>`, `ApiError`)
  usan los mismos nombres que el JSON (`cost_usd`, `total_elements`...), verificados contra
  `/v3/api-docs`. Convertir a camelCase añadiría código de mapeo y otro punto de fallo sin aportar nada:
  el contrato es el de la API.
- **Errores uniformes:** el interceptor convierte todo `HttpErrorResponse` en un `ApiError`: respeta el del
  backend; con status 0 (sin red) usa "No se pudo conectar con el servidor"; con un cuerpo que no es JSON
  (p. ej. 502 del proxy) usa un mensaje genérico según el status. Los componentes nunca ven un error crudo,
  y un error no corta el flujo de carga: se muestra con "Reintentar".
- **Proxy en lugar de CORS:** el navegador solo habla con su propio origen (`/api`), en desarrollo vía
  `proxy.conf.json` y en producción vía nginx, de modo que el backend no necesita configuración CORS ni se
  hardcodea su URL en el bundle.
- **Sin librería de UI ni de iconos:** la pantalla solo necesita una tabla, un formulario y unos pocos
  componentes (paginación, alerta, badge, spinner) que se resuelven con HTML semántico y SCSS propio. Así no
  se añaden dependencias, el bundle es pequeño (~78 kB transferidos en la carga inicial) y la accesibilidad
  (`aria-sort`, `aria-current`, `role="status"`, foco visible, contraste AA) está bajo control. Los tokens de
  diseño son variables CSS en `src/styles/_variables.scss`.
- **`overrides` de vite 8.3.4:** `@angular/build` 22.2.x fija `vite` 8.3.0, afectado por advisories altos del
  servidor de desarrollo (GHSA-9jrq-w75r-8gcw, entre otros). `npm audit fix --force` degradaría Angular a
  22.1.9; en su lugar se fuerza el parche 8.3.4 de la misma línea (solo afecta a herramientas de
  desarrollo). Se puede quitar cuando `@angular/build` publique una versión con vite corregido.
