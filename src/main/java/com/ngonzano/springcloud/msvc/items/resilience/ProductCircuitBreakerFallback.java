package com.ngonzano.springcloud.msvc.items.resilience;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ngonzano.libs.msvc.commons.entities.Product;
import com.ngonzano.springcloud.msvc.items.models.Item;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Fallback invocado exclusivamente por el circuit breaker
 * ({@code CircuitBreakerFactory.run}).
 * Ante fallo del servicio upstream o circuito abierto, devuelve un producto
 * genérico.
 */
@Component
public class ProductCircuitBreakerFallback {

    private final Logger logger = LoggerFactory.getLogger(ProductCircuitBreakerFallback.class);

    public Optional<Product> findById(Long id, Throwable throwable) {
        logFallback(id, throwable);
        return Optional.of(ProductFallback.generic(id));
    }

    public Optional<Item> findItemById(Long id, Integer quantity, Throwable throwable) {
        logFallback(id, throwable);
        return Optional.of(new Item(ProductFallback.generic(id), quantity));
    }

    private void logFallback(Long id, Throwable throwable) {
        if (findCause(throwable, CallNotPermittedException.class) != null) {
            logger.warn("Circuit breaker ABIERTO — producto genérico para id={}", id);
        } else {
            logger.warn("Circuit breaker fallback — producto genérico para id={}: {}",
                    id, throwable.getMessage());
        }
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
