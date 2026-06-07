package com.ngonzano.springcloud.msvc.items.services;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.ngonzano.springcloud.msvc.items.clients.ProductClient;
import com.ngonzano.springcloud.msvc.items.models.Item;
import com.ngonzano.springcloud.msvc.items.models.Product;
import com.ngonzano.springcloud.msvc.items.resilience.ProductCircuitBreakerFallback;
import com.ngonzano.springcloud.msvc.items.resilience.ProductServiceErrorHandler;

@Service
@Primary
public class ItemServiceWebClient implements ItemService {

    private static final int QUANTITY = 2;

    private final ProductClient productClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ProductCircuitBreakerFallback circuitBreakerFallback;

    public ItemServiceWebClient(ProductClient productClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            ProductCircuitBreakerFallback circuitBreakerFallback) {
        this.productClient = productClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.circuitBreakerFallback = circuitBreakerFallback;
    }

    @Override
    public List<Item> findAll() {
        return circuitBreakerFactory.create(ProductClient.CIRCUIT_BREAKER_NAME).run(
                () -> productClient.findAll()
                        .stream()
                        .map(product -> new Item(product, QUANTITY))
                        .collect(Collectors.toList()),
                ProductServiceErrorHandler::rethrow);
    }

    @Override
    public Optional<Item> findById(Long id) {
        return circuitBreakerFactory.create(ProductClient.CIRCUIT_BREAKER_NAME).run(
                () -> productClient.findById(id).map(product -> new Item(product, QUANTITY)),
                throwable -> circuitBreakerFallback.findItemById(id, QUANTITY, throwable));
    }

    @Override
    public Optional<Item> findByIdDetails(Long id) {
        return productClient.findById(id).map(product -> new Item(product, QUANTITY));
    }

    @Override
    public Product save(Product product) {
        return productClient.save(product);
    }

    @Override
    public Product update(Product product, Long id) {
        return productClient.update(product, id);
    }

    @Override
    public void delete(Long id) {
        productClient.delete(id);
    }
}
