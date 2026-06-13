package com.ngonzano.springcloud.msvc.items.resilience;

import com.ngonzano.libs.msvc.commons.entities.Product;

public final class ProductFallback {

    private ProductFallback() {
    }

    public static Product generic(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Producto genérico");
        product.setDescription("Respuesta de respaldo — servicio products no disponible");
        product.setPrice(0.0);
        product.setCategory("fallback");
        return product;
    }
}
