package com.ngonzano.springcloud.msvc.items.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ngonzano.libs.msvc.commons.entities.Product;
import com.ngonzano.springcloud.msvc.items.clients.ProductClient;
import com.ngonzano.springcloud.msvc.items.models.Item;
import com.ngonzano.springcloud.msvc.items.resilience.ProductCircuitBreakerFallback;
import com.ngonzano.springcloud.msvc.items.services.ItemService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

@RefreshScope
@RestController
@RequestMapping("/items")
public class ItemController {

    private static final int QUANTITY = 2;

    private final ItemService service;
    private final ProductCircuitBreakerFallback circuitBreakerFallback;
    private final Logger logger = LoggerFactory.getLogger(ItemController.class);

    @Value("${configuration.texto}")
    private String texto;

    @Autowired
    private Environment env;

    public ItemController(ItemService service, ProductCircuitBreakerFallback circuitBreakerFallback) {
        this.service = service;
        this.circuitBreakerFallback = circuitBreakerFallback;
    }

    @GetMapping("/fetch-config")
    public ResponseEntity<Map<String, String>> fetchConfig(@Value("${configuration.port}") String port) {
        Map<String, String> response = new HashMap<>();
        response.put("texto", texto);
        response.put("port", port);
        if (env.getActiveProfiles().length > 0 && env.getActiveProfiles()[0].equals("dev")) {
            response.put("autor.nombre", env.getProperty("configuration.autor.nombre"));
            response.put("autor.email", env.getProperty("configuration.autor.email"));
        }
        return ResponseEntity.ok(response);
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

    @PostMapping
    public ResponseEntity<Product> save(@RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.update(product, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
