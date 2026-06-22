# msvc-items

Microservicio de **ítems** (producto + cantidad). Agrega datos del catálogo consultando **msvc-products** vía OpenFeign/WebClient y obtiene configuración externa del **config-server**.

## Stack

- Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1
- Puerto: **8002** (local) · **8005** (default remoto) · **8007** (prod) — según perfil y Config Server
- Perfil activo: `dev`
- **Distributed tracing:** [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/) + Zipkin (Brave). Ver [docs/TRACING.md](docs/TRACING.md)

## Endpoints

Base del servicio: `/items`

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/items/fetch-config` | Expone propiedades del Config Server |
| GET | `/items` | Lista ítems (producto + cantidad) |
| GET | `/items/{id}` | Ítem por ID |
| GET | `/items/details/{id}` | Ítem enriquecido con circuit breaker hacia products |
| POST | `/items` | Crea producto (delega a msvc-products) |
| PUT | `/items/{id}` | Actualiza producto |
| DELETE | `/items/{id}` | Elimina producto |

### Vía API Gateway (puerto 8080)

Prefijo: `/api/items/**` (StripPrefix=2). El gateway añade el header `token-request`.

| Método | Ruta Gateway |
|--------|--------------|
| GET | `/api/items/items/fetch-config` |
| GET | `/api/items/items` |
| GET | `/api/items/items/{id}` |
| GET | `/api/items/items/details/{id}` |
| POST | `/api/items/items` |
| PUT | `/api/items/items/{id}` |
| DELETE | `/api/items/items/{id}` |

**Fallback gateway:** `GET /fallback/items` (503 si el circuit breaker se abre)

## Importancia en el ecosistema

Microservicio **agregador** que demuestra patrones avanzados: Config Server, comunicación entre servicios, load balancing y Resilience4j (circuit breaker).

**Dependencias:** Eureka, **config-server**, **spring-cloud-config**, **msvc-products**, **libs-msvc-commons**.

**Consumido por:** **msvc-gateway-server** (proxy).

**Orden de arranque recomendado:** 5.º, después de Eureka, Config Server y msvc-products.

## Tracing (Zipkin)

```bash
cd ../.. && docker compose up -d   # desde msvc-items → raíz SpringCloud; Zipkin en http://localhost:9411
```

Detalle de configuración, propagación Feign/WebClient y verificación: [docs/TRACING.md](docs/TRACING.md).
