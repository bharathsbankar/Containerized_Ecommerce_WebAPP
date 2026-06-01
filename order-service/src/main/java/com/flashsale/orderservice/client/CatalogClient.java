package com.flashsale.orderservice.client;

import com.flashsale.orderservice.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "catalog-service", url = "http://catalog-service:8081")
public interface CatalogClient {

    @GetMapping("/api/products/{id}")
    ProductDto getProductById(@PathVariable("id") Long id);

    @PutMapping("/api/products/{id}/decrement")
    void decrementStock(@PathVariable("id") Long id, @RequestParam("quantity") Integer quantity);
}
