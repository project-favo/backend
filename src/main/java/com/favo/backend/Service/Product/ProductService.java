package com.favo.backend.Service.Product;

import com.favo.backend.Domain.product.*;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
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

    /**
     * Yeni product oluştur
     */
    public ProductResponseDto createProduct(ProductRequestDto request) {
        // Tag kontrolü
        Tag tag = tagRepository.findById(request.getTagId())
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + request.getTagId()));

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
     */
    public ProductResponseDto updateProduct(Long id, ProductRequestDto request) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Tag kontrolü
        if (request.getTagId() != null) {
            Tag tag = tagRepository.findById(request.getTagId())
                    .orElseThrow(() -> new RuntimeException("Tag not found with id: " + request.getTagId()));
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

    /**
     * Product'ı pasif yap (soft delete)
     */
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        product.setIsActive(false);
        productRepository.save(product);
    }
}

