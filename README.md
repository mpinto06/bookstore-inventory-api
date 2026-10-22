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
