package com.favo.backend.Service.Product;

import com.favo.backend.Domain.product.*;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
// import com.favo.backend.Domain.review.Review; // ⚠️ Admin paneli aktifleştirildiğinde kullanılacak
// import com.favo.backend.Domain.review.Repository.ReviewRepository; // ⚠️ Admin paneli aktifleştirildiğinde kullanılacak
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    // private final ReviewRepository reviewRepository; // ⚠️ Admin paneli aktifleştirildiğinde kullanılacak

    /**
     * Yeni product oluştur
     * ÖNEMLİ: Product sadece leaf tag'lere (child'ı olmayan tag'lere) bağlanabilir
     */
    public ProductResponseDto createProduct(ProductRequestDto request) {
        // Tag kontrolü
        Tag tag = tagRepository.findById(request.getTagId())
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + request.getTagId()));

        // Leaf tag kontrolü: Tag'in child'ı olmamalı (sadece en son seviye tag'lere product bağlanabilir)
        if (!tag.getChildren().isEmpty()) {
            // Aktif child var mı kontrol et
            boolean hasActiveChildren = tag.getChildren().stream()
                    .anyMatch(child -> Boolean.TRUE.equals(child.getIsActive()));
            
            if (hasActiveChildren) {
                throw new RuntimeException("Product can only be assigned to leaf tags (tags without children). " +
                        "Tag '" + tag.getCategoryPath() + "' has child tags.");
            }
        }

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setImageURL(request.getImageURL());
        product.setTag(tag);
        product.setCreatedAt(LocalDateTime.now());
        product.setIsActive(true);

        Product saved = productRepository.save(product);
        return ProductMapper.toDto(saved);
    }

    /**
     * Tüm aktif product'ları getir
     */
    public List<ProductResponseDto> getAllActiveProducts() {
        return productRepository.findByIsActiveTrue()
                .stream()
                .map(ProductMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * ID'ye göre aktif product getir
     */
    public ProductResponseDto getProductById(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        return ProductMapper.toDto(product);
    }

    /**
     * Tag'e göre aktif product'ları getir
     */
    public List<ProductResponseDto> getProductsByTagId(Long tagId) {
        return productRepository.findByTagIdAndIsActiveTrue(tagId)
                .stream()
                .map(ProductMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Product güncelle
     * 
     * ⚠️ ŞU AN PASİF: Admin paneli aktifleştirildiğinde kullanılacak
     */
    /*
    public ProductResponseDto updateProduct(Long id, ProductRequestDto request) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Tag kontrolü
        if (request.getTagId() != null) {
            Tag tag = tagRepository.findById(request.getTagId())
                    .orElseThrow(() -> new RuntimeException("Tag not found with id: " + request.getTagId()));
            
            // Leaf tag kontrolü: Tag'in child'ı olmamalı
            if (!tag.getChildren().isEmpty()) {
                boolean hasActiveChildren = tag.getChildren().stream()
                        .anyMatch(child -> Boolean.TRUE.equals(child.getIsActive()));
                
                if (hasActiveChildren) {
                    throw new RuntimeException("Product can only be assigned to leaf tags (tags without children). " +
                            "Tag '" + tag.getCategoryPath() + "' has child tags.");
                }
            }
            
            product.setTag(tag);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getImageURL() != null) {
            product.setImageURL(request.getImageURL());
        }

        Product updated = productRepository.save(product);
        return ProductMapper.toDto(updated);
    }
    */

    /**
     * Product'ı pasif yap (soft delete)
     * ÖNEMLİ: Product silindiğinde ilişkili tüm Review'lar da soft delete yapılır
     * Veriler fiziksel olarak silinmez, sadece isActive = false yapılır
     * 
     * ⚠️ ŞU AN PASİF: Admin paneli aktifleştirildiğinde kullanılacak
     */
    /*
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        
        // Product'ı soft delete yap
        product.setIsActive(false);
        productRepository.save(product);
        
        // Product'a ait tüm aktif Review'ları da soft delete yap
        List<Review> reviews = reviewRepository.findByProductIdAndIsActiveTrue(id);
        for (Review review : reviews) {
            review.setIsActive(false);
            reviewRepository.save(review);
            
            // TODO: İleride Media ve ReviewInteraction implement edildiğinde
            // Review silindiğinde onları da soft delete yapılacak
        }
    }
    */
}

