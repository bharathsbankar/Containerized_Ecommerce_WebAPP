package com.flashsale.orderservice.controller;

import com.flashsale.orderservice.client.CatalogClient;
import com.flashsale.orderservice.dto.OrderRequest;
import com.flashsale.orderservice.dto.ProductDto;
import com.flashsale.orderservice.model.Order;
import com.flashsale.orderservice.repository.OrderRepository;
import com.flashsale.orderservice.exception.ProductNotFoundException;
import com.flashsale.orderservice.exception.InsufficientStockException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5000")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;

    public OrderController(OrderRepository orderRepository, CatalogClient catalogClient) {
        this.orderRepository = orderRepository;
        this.catalogClient = catalogClient;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body("Product ID and a quantity greater than 0 are required.");
        }

        // 1. Fetch product details via OpenFeign
        ProductDto product;
        try {
            product = catalogClient.getProductById(request.getProductId());
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException("Product not found with ID: " + request.getProductId());
        }

        // 2. Validate in-memory stock availability
        if (product.getStockQuantity() < request.getQuantity()) {
            throw new InsufficientStockException("Insufficient stock for product: " + product.getName() + 
                                               ". Requested: " + request.getQuantity() + 
                                               ", Available: " + product.getStockQuantity());
        }

        // 3. Compute absolute total price (unit price * quantity)
        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        // 4. Save order to order_db with status COMPLETED
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(totalPrice);
        order.setOrderStatus("COMPLETED");
        Order savedOrder = orderRepository.save(order);

        // 5. Decrement stock on Catalog Service via Feign
        catalogClient.decrementStock(request.getProductId(), request.getQuantity());

        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder);
    }
}
