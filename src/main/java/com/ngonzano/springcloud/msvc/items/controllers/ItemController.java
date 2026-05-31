package com.ngonzano.springcloud.msvc.items.controllers;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ngonzano.springcloud.msvc.items.clients.ProductClient;
import com.ngonzano.springcloud.msvc.items.models.Item;
import com.ngonzano.springcloud.msvc.items.resilience.ProductCircuitBreakerFallback;
import com.ngonzano.springcloud.msvc.items.services.ItemService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

@RestController
@RequestMapping("/items")
public class ItemController {

    private static final int QUANTITY = 2;

    private final ItemService service;
    private final ProductCircuitBreakerFallback circuitBreakerFallback;
    private final Logger logger = LoggerFactory.getLogger(ItemController.class);

    public ItemController(ItemService service, ProductCircuitBreakerFallback circuitBreakerFallback) {
        this.service = service;
        this.circuitBreakerFallback = circuitBreakerFallback;
    }

    @GetMapping
    public List<Item> findAll(@RequestHeader(value = "token-request", required = false) String tokenRequest,
            @RequestParam(value = "name", required = false) String name) {
        logger.info("tokenRequest: {}", tokenRequest);
        logger.info("name: {}", name);
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> findById(@PathVariable Long id) {
        Optional<Item> item = service.findById(id);
        logger.info("Item obtenido: {}", item);
        return item.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @CircuitBreaker(name = ProductClient.CIRCUIT_BREAKER_NAME, fallbackMethod = "findByIdDetailsFallback")
    @TimeLimiter(name = ProductClient.CIRCUIT_BREAKER_NAME)
    @GetMapping("/details/{id}")
    public ResponseEntity<Item> findByIdDetails(@PathVariable Long id) {
        Optional<Item> item = service.findByIdDetails(id);
        logger.info("Item obtenido (details): {}", item);
        return item.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @SuppressWarnings("unused")
    private ResponseEntity<Item> findByIdDetailsFallback(Long id, Throwable throwable) {
        return ResponseEntity.ok(circuitBreakerFallback.findItemById(id, QUANTITY, throwable).orElseThrow());
    }
}
