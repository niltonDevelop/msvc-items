# Circuit Breaker en msvc-items

Documentación del comportamiento actual del **Circuit Breaker** con **Resilience4j** y **Spring Cloud Circuit Breaker**, aplicado a las llamadas de `msvc-items` hacia `msvc-products`.

---

## Resumen

`msvc-items` consume el microservicio `msvc-products` mediante **WebClient** (implementación activa `@Primary`). Las llamadas remotas están protegidas por un circuit breaker llamado **`products`**.

Cuando la llamada a products **falla** o el circuito está **abierto**, el circuit breaker ejecuta un **fallback** que devuelve un **producto genérico**. El controller responde **200 OK** con ese item de respaldo.

El controller **no** contiene lógica de circuit breaker; toda la resiliencia vive en la capa de cliente.

---

## Arquitectura

```text
ItemController
    └── ItemServiceWebClient (@Primary)
            └── ProductClient                    ← Circuit Breaker aquí
                    ├── supplier  → WebClient → msvc-products (Eureka)
                    └── fallback  → ProductCircuitBreakerFallback
                                          └── ProductFallback.generic(id)
```

### Archivos involucrados

| Archivo | Rol |
|---|---|
| `clients/ProductClient.java` | Envuelve las llamadas HTTP con `CircuitBreakerFactory.run()` |
| `resilience/ProductCircuitBreakerFallback.java` | Fallback invocado **solo** por el circuit breaker |
| `resilience/ProductFallback.java` | Construye el producto genérico de respaldo |
| `resources/application.properties` | Única fuente de configuración Resilience4j (instancia `products`) |
| `services/ItemServiceWebClient.java` | Mapea `Product` → `Item` (quantity = 2) |
| `controllers/ItemController.java` | Expone `/items/{id}` — sin lógica de CB |

Implementación alternativa con **Feign** (`ItemServiceFeign`): usa el mismo circuit breaker `"products"` y el mismo fallback.

---

## Flujo de una petición `GET /items/{id}`

```mermaid
sequenceDiagram
    participant Cliente
    participant Items as msvc-items
    participant CB as Circuit Breaker "products"
    participant Products as msvc-products

    Cliente->>Items: GET /items/{id}
    Items->>CB: run(supplier, fallback)

    alt Circuito CLOSED o HALF_OPEN
        CB->>Products: GET /product/{id}
        alt Respuesta OK
            Products-->>CB: 200 + Product
            CB-->>Items: Optional<Product>
            Items-->>Cliente: 200 + Item real
        else 404 Not Found
            Products-->>CB: 404
            CB-->>Items: Optional.empty()
            Items-->>Cliente: 404
        else Error 5xx o Timeout
            Products-->>CB: fallo
            CB->>CB: fallback
            CB-->>Items: ProductFallback.generic(id)
            Items-->>Cliente: 200 + Item genérico
        end
    else Circuito OPEN
        CB->>CB: fallback (sin llamar a products)
        CB-->>Items: ProductFallback.generic(id)
        Items-->>Cliente: 200 + Item genérico
    end
```

---

## Estados del Circuit Breaker

Resilience4j maneja tres estados:

| Estado | Comportamiento |
|---|---|
| **CLOSED** | Las llamadas pasan a `msvc-products`. Los fallos se registran en la ventana deslizante. |
| **OPEN** | Las llamadas **no** llegan a products. Se ejecuta el fallback directamente (`CallNotPermittedException`). |
| **HALF_OPEN** | Tras el tiempo de espera, permite llamadas de prueba. Si tienen éxito → CLOSED; si fallan → OPEN. |

### Cuándo se abre el circuito

Configuración actual en `application.properties`:

```properties
resilience4j.circuitbreaker.instances.products.slidingWindowSize=10
resilience4j.circuitbreaker.instances.products.minimumNumberOfCalls=5
resilience4j.circuitbreaker.instances.products.failureRateThreshold=50
resilience4j.circuitbreaker.instances.products.waitDurationInOpenState=10s
```

| Propiedad | Valor | Significado |
|---|---|---|
| `slidingWindowSize` | 10 | Evalúa las últimas 10 llamadas |
| `minimumNumberOfCalls` | 5 | No abre el circuito hasta acumular al menos 5 llamadas |
| `failureRateThreshold` | 50 | Se abre si ≥ 50% de las llamadas en la ventana fallaron |
| `waitDurationInOpenState` | 10s | Permanece OPEN durante 10 segundos antes de pasar a HALF_OPEN |

**Ejemplo práctico:** 5 llamadas consecutivas a `/items/10` (todas fallan) → tasa de fallo 100% → circuito **OPEN** en la siguiente evaluación.

> Resilience4j no usa un contador fijo de "N fallos". Calcula la **tasa de fallos** sobre la ventana deslizante.

---

## Time Limiter (timeout)

Además del circuit breaker, hay un **time limiter** de 3 segundos:

```properties
resilience4j.timelimiter.instances.products.timeoutDuration=3s
resilience4j.timelimiter.instances.products.cancelRunningFuture=true
```

Spring Cloud Circuit Breaker + Resilience4j lee estas propiedades automáticamente; no hace falta un `@Configuration` Java para valores numéricos o duraciones.

Si la llamada a products supera 3 segundos, se cancela y el circuit breaker ejecuta el **fallback**.

---

## Casos de prueba

Base URL de items: `http://localhost:8002`

### Prerrequisitos

1. Eureka en `http://localhost:8761`
2. `msvc-products` registrado en Eureka
3. `msvc-items` en el puerto **8002**
4. Al menos un producto válido en BD (por ejemplo id **1**)

Para resetear el estado del circuit breaker entre sesiones de prueba, reinicia `msvc-items`.

---

### CP-01 — CLOSED: respuesta exitosa (item real)

| Campo | Valor |
|---|---|
| **Estado CB** | CLOSED |
| **Objetivo** | Verificar flujo normal sin fallback |
| **Petición** | `GET http://localhost:8002/items/1` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item real con datos de BD (`category` ≠ `"fallback"`) |
| **Log items** | Sin mensajes de fallback |
| **Log products** | Query Hibernate normal |

```bash
curl -i http://localhost:8002/items/1
```

---

### CP-02 — CLOSED: producto inexistente (404 sin fallback)

| Campo | Valor |
|---|---|
| **Estado CB** | CLOSED |
| **Objetivo** | Un 404 real de products **no** activa el fallback |
| **Petición** | `GET http://localhost:8002/items/99999` |
| **HTTP esperado** | `404 Not Found` |
| **Body esperado** | Vacío (sin item genérico) |
| **Log items** | Sin mensajes de fallback |

```bash
curl -i http://localhost:8002/items/99999
```

---

### CP-03 — CLOSED: error upstream simulado (id=10)

| Campo | Valor |
|---|---|
| **Estado CB** | CLOSED |
| **Objetivo** | products responde 500 → fallback del CB |
| **Petición** | `GET http://localhost:8002/items/10` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item genérico (`"category": "fallback"`, `"name": "Producto genérico"`) |
| **Tiempo aprox.** | < 1s |
| **Log items** | `Circuit breaker fallback — producto genérico para id=10: ...` |
| **Log products** | Responde 500 controlado (sin stack trace) |

```bash
curl -i http://localhost:8002/items/10
```

---

### CP-04 — CLOSED: timeout simulado (id=7)

| Campo | Valor |
|---|---|
| **Estado CB** | CLOSED |
| **Objetivo** | products tarda 5s, time limiter corta a 3s → fallback |
| **Petición** | `GET http://localhost:8002/items/7` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item genérico (`"category": "fallback"`) |
| **Tiempo aprox.** | ~3 segundos (no 5) |
| **Log items** | `Circuit breaker fallback — producto genérico para id=7: ...` |

```bash
curl -i -w "\nTiempo total: %{time_total}s\n" http://localhost:8002/items/7
```

---

### CP-05 — OPEN: apertura del circuito tras fallos acumulados

| Campo | Valor |
|---|---|
| **Estado CB** | CLOSED → OPEN |
| **Objetivo** | Acumular fallos hasta superar umbral (≥5 llamadas, ≥50% fallos) |
| **Petición** | `GET http://localhost:8002/items/10` repetido **6 veces** |
| **HTTP esperado** | `200 OK` en todas (item genérico) |
| **Log items (intentos 1–5)** | `Circuit breaker fallback — producto genérico para id=10` |
| **Log items (intento 6+)** | `Circuit breaker ABIERTO — producto genérico para id=10` |
| **Diferencia clave** | Tras OPEN, la respuesta es más rápida (no llama a products) |

```bash
for i in {1..6}; do
  echo "--- Intento $i ---"
  curl -s -o /dev/null -w "HTTP %{http_code} en %{time_total}s\n" \
    http://localhost:8002/items/10
done
```

**Criterio de éxito:** a partir del intento 6 aparece el log **ABIERTO** y el tiempo de respuesta baja respecto a los primeros intentos.

---

### CP-06 — HALF_OPEN → CLOSED: recuperación del circuito

| Campo | Valor |
|---|---|
| **Estado CB** | OPEN → HALF_OPEN → CLOSED |
| **Precondición** | CP-05 completado (circuito OPEN) |
| **Paso 1** | Esperar **10 segundos** (`waitDurationInOpenState=10s`) |
| **Paso 2** | `GET http://localhost:8002/items/1` (id válido en BD) |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item **real** (no genérico) |
| **Log items** | Sin fallback |
| **Resultado** | CB vuelve a **CLOSED** |

```bash
echo "Esperando 10s para HALF_OPEN..."
sleep 10
curl -i http://localhost:8002/items/1
```

---

### CP-07 — HALF_OPEN → OPEN: fallo en prueba de recuperación

| Campo | Valor |
|---|---|
| **Estado CB** | OPEN → HALF_OPEN → OPEN |
| **Precondición** | CP-05 completado + esperar 10s |
| **Petición** | `GET http://localhost:8002/items/10` |
| **HTTP esperado** | `200 OK` (item genérico) |
| **Log items** | Fallback activado de nuevo |
| **Resultado** | CB regresa a **OPEN** por fallo en half-open |

```bash
sleep 10
curl -i http://localhost:8002/items/10
```

---

### CP-08 — OPEN: llamada con circuito abierto (cualquier id)

| Campo | Valor |
|---|---|
| **Estado CB** | OPEN |
| **Precondición** | CP-05 completado, sin esperar recuperación |
| **Petición** | `GET http://localhost:8002/items/1` |
| **HTTP esperado** | `200 OK` |
| **Body esperado** | Item genérico (aunque el id exista en BD) |
| **Log items** | `Circuit breaker ABIERTO — producto genérico para id=1` |
| **Nota** | Con circuito OPEN, **no** se consulta products |

```bash
curl -i http://localhost:8002/items/1
```

---

### CP-09 — findAll: error sin fallback genérico

| Campo | Valor |
|---|---|
| **Estado CB** | CLOSED |
| **Objetivo** | Verificar que `findAll` **no** devuelve item genérico |
| **Precondición** | Detener `msvc-products` o provocar error en listado |
| **Petición** | `GET http://localhost:8002/items` |
| **HTTP esperado** | `502`, `504` o `503` (según tipo de fallo) |
| **Body esperado** | `{"error":"..."}` |
| **Nota** | Solo `findById` usa fallback genérico; `findAll` propaga excepción |

```bash
curl -i http://localhost:8002/items
```

---

### Matriz resumen de casos

| Caso | Endpoint | Estado CB | HTTP | ¿Fallback? | Identificador en body |
|---|---|---|---|---|---|
| CP-01 | `/items/1` | CLOSED | 200 | No | Datos reales de BD |
| CP-02 | `/items/99999` | CLOSED | 404 | No | — |
| CP-03 | `/items/10` | CLOSED | 200 | Sí | `"category": "fallback"` |
| CP-04 | `/items/7` | CLOSED | 200 | Sí (~3s) | `"category": "fallback"` |
| CP-05 | `/items/10` ×6 | CLOSED→OPEN | 200 | Sí | Log **ABIERTO** |
| CP-06 | `/items/1` tras 10s | HALF_OPEN→CLOSED | 200 | No | Datos reales |
| CP-07 | `/items/10` tras 10s | HALF_OPEN→OPEN | 200 | Sí | Item genérico |
| CP-08 | `/items/1` con CB OPEN | OPEN | 200 | Sí | Item genérico |
| CP-09 | `/items` sin products | CLOSED | 5xx | No (excepción) | `{"error":"..."}` |

---

### Orden recomendado (Postman o manual)

Ejecuta los casos en este orden para recorrer todos los estados sin interferencias:

```text
CP-01  →  CP-02  →  CP-03  →  CP-04  →  CP-05  →  CP-08  →  CP-06  →  CP-07
```

CP-09 es independiente (requiere detener products).

---

### Checklist de verificación

- [ ] Item real cuando products responde OK (CP-01)
- [ ] 404 sin fallback cuando el producto no existe (CP-02)
- [ ] Item genérico ante error 500 de products (CP-03)
- [ ] Item genérico ante timeout ~3s (CP-04)
- [ ] Log **ABIERTO** tras acumular fallos (CP-05)
- [ ] Recuperación a item real tras espera de 10s (CP-06)
- [ ] Reapertura del circuito si half-open falla (CP-07)
- [ ] Fallback genérico incluso para ids válidos con CB OPEN (CP-08)

---

Cuando el circuit breaker activa el fallback, la respuesta tiene esta forma:

```json
{
  "product": {
    "id": 10,
    "name": "Producto genérico",
    "description": "Respuesta de respaldo — servicio products no disponible",
    "price": 0.0,
    "category": "fallback"
  },
  "quantity": 2,
  "total": 0.0
}
```

Identificar fallback: `"category": "fallback"`.

### Datos simulados en msvc-products

`ProductController` expone estos ids especiales para las pruebas:

| ID | Comportamiento en products | Caso de prueba |
|---|---|---|
| **10** | Responde **500** con `{"error":"Producto no encontrado"}` | CP-03, CP-05, CP-07 |
| **7** | `sleep(5s)` antes de responder | CP-04 |
| **Cualquier otro** | Consulta normal a BD | CP-01, CP-02, CP-06 |

---

## Logs

En la consola de `msvc-items` aparecen dos mensajes distintos:

```text
# Fallo en la llamada, circuito aún CLOSED
Circuit breaker fallback — producto genérico para id=10: ...

# Circuito OPEN — no se llama a products
Circuit breaker ABIERTO — producto genérico para id=10
```

---

## Diferencia: `findById` vs `findAll`

| Método | Circuit Breaker | Fallback |
|---|---|---|
| `ProductClient.findById()` | Sí | Item genérico vía `ProductCircuitBreakerFallback` |
| `ProductClient.findAll()` | Sí | Relanza excepción vía `ProductServiceErrorHandler` (502/504/503) |

Solo `findById` tiene fallback con producto genérico. `findAll` propaga errores al `ItemExceptionHandler`.

---

## Cómo ajustar la sensibilidad del circuito

Para que se abra con **menos fallos**, por ejemplo tras 3 fallos consecutivos:

```properties
resilience4j.circuitbreaker.instances.products.slidingWindowSize=3
resilience4j.circuitbreaker.instances.products.minimumNumberOfCalls=3
resilience4j.circuitbreaker.instances.products.failureRateThreshold=100
```

Reinicia `msvc-items` después de cambiar propiedades.

---

## Dependencia Maven

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

---

## Referencia rápida

```text
Nombre del circuit breaker : "products"
Timeout                    : 3 segundos
Mínimo llamadas p/ evaluar : 5
Umbral de fallos           : 50%
Ventana deslizante         : 10 llamadas
Tiempo en OPEN             : 10 segundos
Puerto items               : 8002
Endpoint                   : GET /items/{id}
```
