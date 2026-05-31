package com.ngonzano.springcloud.msvc.items.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Item {
    private Product product;
    private Integer quantity;
    private Double total;

    /** obtiene el total de un item
     * @param product the product
     * @param quantity the quantity
     * @return el total de un item
     */
    public Item(Product product, Integer quantity) {
        this.product = product;
        this.quantity = quantity;
        this.total = product.getPrice() * quantity;
    }
}
