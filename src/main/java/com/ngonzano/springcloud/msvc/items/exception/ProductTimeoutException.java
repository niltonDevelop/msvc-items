package com.ngonzano.springcloud.msvc.items.exception;

public class ProductTimeoutException extends RuntimeException {

    public ProductTimeoutException() {
        super("Timeout al consultar el servicio de productos");
    }
}
