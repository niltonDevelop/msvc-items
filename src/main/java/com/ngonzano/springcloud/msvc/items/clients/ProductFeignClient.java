package com.ngonzano.springcloud.msvc.items.clients;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ngonzano.springcloud.msvc.items.models.Product;

@FeignClient(name = "msvc-products")
public interface ProductFeignClient {

    @GetMapping("/product")
    List<Product> findAll();

    @GetMapping("/product/{id}")
    Product details(@PathVariable Long id);

}
