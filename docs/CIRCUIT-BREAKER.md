# Circuit Breaker en msvc-items

> Documentación canónica: **[../CIRCUIT-BREAKER.md](../CIRCUIT-BREAKER.md)**

Resumen de la implementación actual:

## Dos enfoques, una instancia (`products`)

| Enfoque | Endpoint | Ubicación |
|---|---|---|
| Programático | `GET /items/{id}` | `ItemServiceWebClient` — `CircuitBreakerFactory.run()` |
| Declarativo | `GET /items/details/{id}` | `ItemController` — `@CircuitBreaker` + `@TimeLimiter` |

- Configuración: `src/main/resources/application.yml`
- Cliente HTTP sin resiliencia: `ProductClient`
- Fallback compartido: `ProductCircuitBreakerFallback` → `ProductFallback.generic(id)`

## Dependencias

- `spring-cloud-starter-circuitbreaker-resilience4j`
- `spring-boot-starter-aspectj` (requerido para anotaciones en Spring Boot 4)

Ver el documento principal para diagramas, casos de prueba CP-01 a CP-10 y referencia de propiedades YAML.
