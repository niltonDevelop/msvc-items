package com.ngonzano.springcloud.msvc.items.exception;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException() {
        super("Servicio de productos no disponible temporalmente");
    }
}
