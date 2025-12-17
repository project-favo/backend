package com.favo.backend.controller;

import com.favo.backend.Domain.product.ProductRequestDto;
import com.favo.backend.Domain.product.ProductResponseDto;
import com.favo.backend.Service.Product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Yeni product oluştur
     * POST /api/products
     */
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody ProductRequestDto request) {
        ProductResponseDto created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Tüm aktif product'ları getir
     * GET /api/products
     */
    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        List<ProductResponseDto> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * ID'ye göre product getir
     * GET /api/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        ProductResponseDto product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    /**
     * Tag'e göre product'ları getir
     * GET /api/products/tag/{tagId}
     */
    @GetMapping("/tag/{tagId}")
    public ResponseEntity<List<ProductResponseDto>> getProductsByTag(@PathVariable Long tagId) {
        List<ProductResponseDto> products = productService.getProductsByTagId(tagId);
        return ResponseEntity.ok(products);
    }

    /**
     * Product güncelle
     * PUT /api/products/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductRequestDto request
    ) {
        ProductResponseDto updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Product'ı sil (soft delete - isActive = false)
     * DELETE /api/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}

