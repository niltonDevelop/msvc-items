package com.ngonzano.springcloud.msvc.items.clients;

import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.ngonzano.springcloud.msvc.items.models.Product;

import reactor.core.publisher.Mono;

@Component
public class ProductClient {

    public static final String CIRCUIT_BREAKER_NAME = "products";

    private final WebClient.Builder webClientBuilder;

    public ProductClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public List<Product> findAll() {
        return webClientBuilder.build()
                .get()
                .uri("/product")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(Product.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .collectList()
                .block();
    }

    public Optional<Product> findById(Long id) {
        return webClientBuilder.build()
                .get()
                .uri("/product/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Product.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .blockOptional();
    }

    public Product save(Product product) {
        return webClientBuilder.build()
                .post()
                .uri("/product")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product)
                .retrieve()
                .bodyToMono(Product.class)
                .block();
    }

    public Product update(Product product, Long id) {
        return webClientBuilder.build()
                .put()
                .uri("/product/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(product)
                .retrieve()
                .bodyToMono(Product.class)
                .block();
    }

    public void delete(Long id) {
        webClientBuilder.build()
                .delete()
                .uri("/product/{id}", id)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
