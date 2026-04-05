package com.favo.backend.controller;

import com.favo.backend.Domain.product.ProductRequestDto;
import com.favo.backend.Domain.product.ProductResponseDto;
import com.favo.backend.Domain.product.ProductSearchResultDto;
import com.favo.backend.Service.Product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product Controller
 * Product CRUD işlemleri için endpoint'ler
 * 
 * ÖNEMLİ: Product sadece leaf tag'lere (child'ı olmayan tag'lere) bağlanabilir
 * Örnek: "Electronic.Telephone.MobilePhone.Iphone.Iphone13" → ✅ Product bağlanabilir
 *        "Electronic.Telephone" → ❌ Product bağlanamaz (child'ı var)
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 🆕 Yeni product oluştur
     * POST /api/products
     * 
     * Body: {
     *   "name": "iPhone 13 Pro",
     *   "description": "Apple iPhone 13 Pro 128GB",
     *   "imageURL": "https://...",
     *   "tagId": 123  // Leaf tag ID (child'ı olmayan tag)
     * }
     * 
     * Response: 201 Created + ProductResponseDto
     * Error: 400 Bad Request - Tag leaf tag değilse veya tag bulunamazsa
     * RBAC: Sadece ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody ProductRequestDto request) {
        ProductResponseDto created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 📋 Tüm aktif product'ları getir
     * GET /api/products
     * 
     * Response: 200 OK + List<ProductResponseDto>
     */
    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        List<ProductResponseDto> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * 🔍 ID'ye göre product getir
     * GET /api/products/{id}
     * 
     * Response: 200 OK + ProductResponseDto
     * Error: 404 Not Found - Product bulunamazsa veya pasifse
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        ProductResponseDto product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    /**
     * 🏷️ Tag'e göre product'ları getir
     * GET /api/products/tag/{tagId}
     * 
     * Belirli bir tag'e ait tüm aktif product'ları döner
     * 
     * Response: 200 OK + List<ProductResponseDto>
     */
    @GetMapping("/tag/{tagId}")
    public ResponseEntity<List<ProductResponseDto>> getProductsByTag(@PathVariable Long tagId) {
        List<ProductResponseDto> products = productService.getProductsByTagId(tagId);
        return ResponseEntity.ok(products);
    }

    /**
     * 🏠 Ana sayfa feed
     * GET /api/products/home
     *
     * Sıradan ilk 20 ürün (en yeni önce), her sayfada 20 ürün. Pagination: page=0,1,2... size=20 (varsayılan).
     *
     * Örnek: GET /api/products/home?page=0&size=20
     * Response: 200 OK + ProductSearchResultDto (content, totalElements, totalPages, size, number)
     */
    @GetMapping("/home")
    public ResponseEntity<ProductSearchResultDto> getHomeFeed(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductSearchResultDto result = productService.getHomeFeed(pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 🔍 Search & Filter API
     * GET /api/products/search
     *
     * Parametreler (hepsi opsiyonel, birleştirilebilir):
     * - q: Ürün adı veya açıklamada aranacak metin (case-insensitive)
     * - tagIds: Bu tag ID'lerinden birine ait ürünler (örn: tagIds=1&tagIds=2)
     * - categoryPathPrefix: Tag categoryPath bu prefix ile başlayan ürünler (örn: Electronic.Telephone)
     * - page: Sayfa numarası (0'dan başlar, varsayılan 0)
     * - size: Sayfa boyutu (varsayılan 20)
     *
     * Örnek: GET /api/products/search?q=iphone&tagIds=5&tagIds=6&page=0&size=10
     *
     * Response: 200 OK + ProductSearchResultDto (content, totalElements, totalPages, size, number)
     */
    @GetMapping("/search")
    public ResponseEntity<ProductSearchResultDto> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String categoryPathPrefix,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductSearchResultDto result = productService.searchAndFilter(q, tagIds, categoryPathPrefix, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * ✏️ Product güncelle
     * PUT /api/products/{id}
     *
     * Partial update: Sadece gönderilen field'lar güncellenir
     *
     * Body: {
     *   "name": "Updated Name",  // Opsiyonel
     *   "description": "...",     // Opsiyonel
     *   "imageURL": "...",        // Opsiyonel
     *   "tagId": 456              // Opsiyonel (leaf tag olmalı)
     * }
     *
     * Response: 200 OK + ProductResponseDto
     * Error: 404 Not Found - Product bulunamazsa
     * Error: 400 Bad Request - Tag leaf tag değilse
     * RBAC: Sadece ADMIN (Bearer + ROLE_ADMIN).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductRequestDto request
    ) {
        ProductResponseDto updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 🗑️ Product'ı sil (soft delete - isActive = false)
     * DELETE /api/products/{id}
     * 
     * Product fiziksel olarak silinmez, sadece isActive = false yapılır
     * İlişkili tüm Review'lar da soft delete yapılır
     * 
     * Response: 204 No Content
     * Error: 404 Not Found - Product bulunamazsa veya zaten pasifse
     * RBAC: Sadece ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}

