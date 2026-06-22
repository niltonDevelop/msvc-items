# Distributed Tracing (ecosistema Spring Cloud)

> **Guía completa:** [docs/ZIPKIN.md](../../docs/ZIPKIN.md) — conceptos, UI, tags, sampling, prod, troubleshooting.

Todos los microservicios usan [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/) + **Brave** + **Zipkin** compartido (`SpringCloud/docker-compose.yml`).

| Servicio | Rol en la traza |
|----------|-----------------|
| **msvc-gateway-server** | Span raíz (entrada del cliente) |
| **oauth** | Span hijo + Feign → users |
| **msvc-users** | Span hijo (auth interna / API v1/v2) |
| **msvc-items** | Span hijo + Feign/WebClient → products |
| **msvc-products** | Span hijo (catálogo) |

## ¿Qué se instrumenta en msvc-items?

| Componente | Span automático |
|------------|-----------------|
| Peticiones HTTP entrantes (`/items/**`) | Sí |
| OpenFeign → `msvc-products` | Sí (propagación W3C/B3) |
| WebClient → `msvc-products` | Sí (usa `WebClient.Builder` auto-configurado) |
| Resilience4j Circuit Breaker | Sí (via Observation API) |
| Logs | Correlación `[msvc-items,traceId,spanId]` |

> **msvc-products** también debe tener `spring-boot-starter-zipkin` para ver spans hijos en Zipkin. Ver [msvc-products/docs/TRACING.md](../../msvc-products/docs/TRACING.md).

## Dependencia Maven

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-zipkin</artifactId>
</dependency>
```

Incluye `spring-boot-micrometer-tracing-brave`, `micrometer-tracing-bridge-brave` y el reporter Zipkin.

## Arrancar Zipkin (local)

Un solo Zipkin para todo el ecosistema. Desde la raíz del monorepo (`SpringCloud/`):

```bash
cd ../..   # o cd /ruta/a/SpringCloud
docker compose up -d
```

Abre la UI en [http://localhost:9411](http://localhost:9411).

## Configuración

| Propiedad | Dev | Prod (default) | Descripción |
|-----------|-----|----------------|-------------|
| `management.tracing.sampling.probability` | `1.0` | `0.1` | Fracción de trazas enviadas |
| `management.tracing.export.zipkin.endpoint` | `http://localhost:9411/api/v2/spans` | `${ZIPKIN_ENDPOINT:...}` | URL del collector Zipkin |
| `logging.pattern.correlation` | Sleuth-style | Sleuth-style | IDs en logs |

La configuración base está en `application.yml`; los perfiles `dev`/`prod` la refinan desde el **config-server** (`spring-cloud-config/msvc-items-*.properties`).

## Flujo de trazas en el ecosistema

```
Cliente → gateway → oauth / users / items → products
          traceId ─────────── compartido en todos los hops ──────────
```

## Verificar (items + products)

1. Arranca Zipkin desde `SpringCloud/`: `docker compose up -d`
2. Arranca Eureka, **msvc-products**, msvc-items (products antes que items)
3. Llama a `GET http://localhost:8002/items`
4. En Zipkin UI → **Run Query** → traza con spans:
   - `http get /items` (msvc-items)
   - `http get /product` (msvc-products, span hijo con el mismo traceId)
5. Revisa logs de ambos servicios: `[msvc-items,...]` y `[msvc-products,...]` con el mismo traceId

## Spans personalizados (opcional)

Para operaciones de negocio que no son HTTP, usa la Observation API:

```java
@Component
class MiComponente {

    private final ObservationRegistry observationRegistry;

    MiComponente(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    void operacion() {
        Observation.createNotStarted("items.aggregate", observationRegistry)
            .lowCardinalityKeyValue("operation", "findAll")
            .observe(() -> {
                // lógica de negocio
            });
    }
}
```

## Referencias

- [Micrometer Tracing — documentación oficial](https://docs.micrometer.io/tracing/reference/)
- [Spring Boot — Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Zipkin Quickstart](https://zipkin.io/pages/quickstart.html)
