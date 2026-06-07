package com.ngonzano.springcloud.msvc.items.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import com.ngonzano.springcloud.msvc.items.clients.ProductClient;
import com.ngonzano.springcloud.msvc.items.clients.ProductFeignClient;
import com.ngonzano.springcloud.msvc.items.models.Item;
import com.ngonzano.springcloud.msvc.items.models.Product;
import com.ngonzano.springcloud.msvc.items.resilience.ProductCircuitBreakerFallback;
import com.ngonzano.springcloud.msvc.items.resilience.ProductServiceErrorHandler;

import feign.FeignException;

@Service
public class ItemServiceFeign implements ItemService {

    private static final int QUANTITY = 3;

    private final ProductFeignClient productFeignClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ProductCircuitBreakerFallback circuitBreakerFallback;

    public ItemServiceFeign(ProductFeignClient productFeignClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            ProductCircuitBreakerFallback circuitBreakerFallback) {
        this.productFeignClient = productFeignClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.circuitBreakerFallback = circuitBreakerFallback;
    }

    @Override
    public List<Item> findAll() {
        return circuitBreakerFactory.create(ProductClient.CIRCUIT_BREAKER_NAME).run(
                () -> productFeignClient.findAll()
                        .stream()
                        .map(product -> new Item(product, QUANTITY))
                        .collect(Collectors.toList()),
                ProductServiceErrorHandler::rethrow);
    }

    @Override
    public Optional<Item> findById(Long id) {
        return circuitBreakerFactory.create(ProductClient.CIRCUIT_BREAKER_NAME).run(
                () -> {
                    try {
                        Product product = productFeignClient.details(id);
                        if (product == null) {
                            return Optional.empty();
                        }
                        return Optional.of(new Item(product, QUANTITY));
                    } catch (FeignException.NotFound e) {
                        return Optional.empty();
                    }
                },
                throwable -> circuitBreakerFallback.findItemById(id, QUANTITY, throwable));
    }

    @Override
    public Optional<Item> findByIdDetails(Long id) {
        try {
            Product product = productFeignClient.details(id);
            if (product == null) {
                return Optional.empty();
            }
            return Optional.of(new Item(product, QUANTITY));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public Product save(Product product) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Product update(Product product, Long id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
