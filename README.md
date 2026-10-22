# bookstore-inventory-api

API REST de inventario de librerías con una SPA en Angular que la consume.

## Requisitos

- Java 21
- Docker (con Docker Compose) para la base de datos PostgreSQL 18

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

4. Tests: `./mvnw test` (el test `contextLoads` necesita la BD levantada).

### Reiniciar la base de datos desde cero

```bash
docker compose down -v
docker compose up -d --wait
```

`-v` elimina el volumen con los datos. Es necesario porque, mientras el proyecto está en
desarrollo, las migraciones se **editan** en lugar de apilar `ALTER`s (V2, V3...) para mantener
un historial limpio; Flyway detectaría el cambio de checksum en una migración ya aplicada, así
que hay que recrear la BD para re-aplicarla.

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
| 409 | ISBN duplicado |
| 503 | base de datos no disponible |
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
