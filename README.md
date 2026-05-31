# msvc-items

Microservicio **Spring Boot** que expone una API REST de **ítems** (producto + cantidad + total). Los datos de catálogo se obtienen del microservicio **products** vía **`WebClient`** (implementación **`@Primary`**) o **Feign**, con **Spring Cloud LoadBalancer** e instancias en `application.properties`.

## Stack

| Componente | Versión / notas |
|------------|------------------|
| Java | 21 (`java.version` en `pom.xml`) |
| Spring Boot | 4.0.6 |
| Spring Cloud BOM | 2025.1.1 (`spring-cloud.version`) |

## Estructura del código (`src/main/java`)

| Paquete / clase | Rol |
|-----------------|-----|
| `MsvcItemsApplication` | Arranque; `@SpringBootApplication`, `@EnableFeignClients` |
| `controllers.ItemController` | REST: listado y detalle bajo `/items` |
| `services.ItemService` | Contrato del servicio de ítems |
| `services.ItemServiceFeign` | Implementación Feign (no activa si hay otra `@Primary`) |
| `services.ItemServiceWebClient` | Implementación con **`WebClient`** + `@LoadBalanced` (marcada **`@Primary`**) |
| `clients.ProductFeignClient` | Cliente Feign hacia `msvc-products` |
| `clients.WebClientConfig` | Bean **`WebClient.Builder`** con **`@LoadBalanced`** |
| `models.Item` | DTO de ítem (producto, cantidad, total) |
| `models.Product` | DTO alineado al JSON devuelto por **products** (incl. campos opcionales como `port` si los rellenas en código) |

Configuración: `src/main/resources/application.properties`.  
Depuración en VS Code / Cursor: `.vscode/launch.json` (opcional).

## Dependencias Maven (`pom.xml`)

Dependencias directas principales:

| Artefacto | Uso |
|-----------|-----|
| `spring-boot-starter-webmvc` | API REST (Servlet / Spring MVC) |
| `spring-boot-starter-webflux` | **`WebClient`** (HTTP reactivo) |
| `spring-webflux` | Dependencia explícita del módulo WebFlux (classpath estable con el IDE) |
| `spring-cloud-starter` | Infraestructura Spring Cloud |
| `spring-cloud-starter-openfeign` | Clientes HTTP declarativos (`@FeignClient`) |
| `spring-cloud-starter-loadbalancer` | Balanceo de llamadas Feign por **nombre de servicio** (`msvc-products`) |
| `spring-cloud-starter-circuitbreaker-resilience4j` | Circuit breaker y time limiter hacia **products** (ver [CIRCUIT-BREAKER.md](./CIRCUIT-BREAKER.md)) |
| `lombok` | Reducción de boilerplate (`@Getter` / `@Setter`, etc.) |
| `spring-boot-configuration-processor` | Metadatos de configuración para el IDE (opcional, procesador de anotaciones) |
Tests:

| Artefacto | Uso |
|-----------|-----|
| `spring-boot-starter-webmvc-test` | Tests con stack MVC/test de Spring Boot |

El BOM de Spring Cloud se importa con `spring-cloud-dependencies` `${spring-cloud.version}`.

## Configuración relevante

- **`server.port=8002`**: API de este microservicio.
- **`spring.cloud.discovery.client.simple.instances.msvc-products[*].uri`**: instancias del servicio **products** para el LoadBalancer (solo **host:puerto**; el prefijo de API va en Feign, ver abajo).
- El cliente Feign usa **`path = "/api/v1"`** y rutas **`/product`**, **`/product/{id}`**, equivalentes a **`/api/v1/product`** en **products**.

Si solo tienes **una** instancia de products, puedes dejar una sola entrada en `simple.instances` o levantar también el segundo puerto; si no, el balanceador puede alternar y fallar de forma intermitente.

## Circuit Breaker

Las llamadas a **msvc-products** están protegidas con **Resilience4j** (instancia `products`): time limiter, estados CLOSED/OPEN/HALF_OPEN y fallback con item genérico.

Documentación completa (arquitectura, configuración, **9 casos de prueba** CP-01 a CP-09, logs): **[CIRCUIT-BREAKER.md](./CIRCUIT-BREAKER.md)**

## Endpoints HTTP (este servicio)

Base: `http://localhost:8002`

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/items` | Lista de ítems |
| GET | `/items/{id}` | Ítem para el producto `{id}`; 404 con mensaje si no hay producto |

Ejemplo:

```bash
curl -s http://localhost:8002/items
curl -s http://localhost:8002/items/1
```

## Cómo ejecutar

```bash
./mvnw spring-boot:run
```

Requisito: el microservicio **products** debe estar disponible en las URIs configuradas (por defecto `http://localhost:8001` y opcionalmente `http://localhost:9001`), exponiendo **`GET /api/v1/product`** y **`GET /api/v1/product/{id}`** según tu versión de **products**.

## Build

```bash
./mvnw clean package
```
