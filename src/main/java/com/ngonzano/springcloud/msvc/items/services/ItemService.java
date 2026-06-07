package com.ngonzano.springcloud.msvc.items.services;

import java.util.List;
import java.util.Optional;

import com.ngonzano.springcloud.msvc.items.models.Item;
import com.ngonzano.springcloud.msvc.items.models.Product;

public interface ItemService {
    List<Item> findAll();

    Optional<Item> findById(Long id);

    Optional<Item> findByIdDetails(Long id);

    Product save(Product product);

    Product update(Product product, Long id);

    void delete(Long id);
    // ItemDto save(ItemDto itemDto);
    // ItemDto update(ItemDto itemDto);
    // void delete(Long id);
}
