package com.flashsale.catalogservice.controller;

import com.flashsale.catalogservice.model.Product;
import com.flashsale.catalogservice.repository.ProductRepository;
import com.flashsale.catalogservice.exception.ProductNotFoundException;
import com.flashsale.catalogservice.exception.InsufficientStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "http://localhost:5000")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
    }

    @PutMapping("/{id}/decrement")
    public ResponseEntity<?> decrementStock(@PathVariable Long id, @RequestParam Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body("Quantity must be greater than 0");
        }

        // Verify product existence
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        // Execute the atomic database decrement
        int rowsUpdated = productRepository.decrementStock(id, quantity);
        if (rowsUpdated == 0) {
            throw new InsufficientStockException("Insufficient stock for product ID: " + id + 
                                               ". Available: " + product.getStockQuantity());
        }

        // Fetch and return the updated state of the product
        Product updatedProduct = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found after updating."));
        return ResponseEntity.ok(updatedProduct);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody Product product) {
        return productRepository.save(product);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPrice(productDetails.getPrice());
        product.setStockQuantity(productDetails.getStockQuantity());

        return productRepository.save(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        productRepository.delete(product);
        return ResponseEntity.ok().body("Product deleted successfully");
    }
}
