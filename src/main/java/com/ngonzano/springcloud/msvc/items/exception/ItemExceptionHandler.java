package com.ngonzano.springcloud.msvc.items.exception;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ItemExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(ItemExceptionHandler.class);

    @ExceptionHandler(ProductTimeoutException.class)
    public ResponseEntity<Map<String, String>> handleTimeout(ProductTimeoutException ex) {
        logger.error("Timeout al consultar products");
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Collections.singletonMap("error", ex.getMessage()));
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(ProductUnavailableException ex) {
        logger.error("Circuit breaker abierto para products");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Collections.singletonMap("error", ex.getMessage()));
    }

    @ExceptionHandler(ProductUpstreamException.class)
    public ResponseEntity<Map<String, String>> handleUpstream(ProductUpstreamException ex) {
        logger.error("Error en el servicio products: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Collections.singletonMap("error", ex.getMessage()));
    }
}
