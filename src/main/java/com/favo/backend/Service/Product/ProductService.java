package com.favo.backend.Service.Product;

import com.favo.backend.Domain.product.*;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    private final ReviewRepository reviewRepository;

    /**
     * Yeni product oluştur
     * ÖNEMLİ: Product sadece leaf tag'lere (child'ı olmayan tag'lere) bağlanabilir
     */
    public ProductResponseDto createProduct(ProductRequestDto request) {
        // Tag kontrolü
        Tag tag = tagRepository.findById(request.getTagId())
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + request.getTagId()));

        // Tag'in aktif olup olmadığını kontrol et
        if (!Boolean.TRUE.equals(tag.getIsActive())) {
            throw new RuntimeException("Cannot assign product to inactive tag. Tag id: " + request.getTagId());
        }

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
     * Ana sayfa feed: Önce sayfa ID'leri çekilir (sıra sabit), sonra entity'ler fetch join ile.
     * Böylece Hibernate DISTINCT+FETCH sayfa kayması (1 ürün gelmesi) olmaz.
     */
    public ProductSearchResultDto getHomeFeed(Pageable pageable) {
        Pageable safe = pageable != null ? pageable : PageRequest.of(0, 6);
        Page<Long> idPage = productRepository.findActiveProductIdsOrderByCreatedAtDescIdAsc(safe);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new ProductSearchResultDto(List.of(), idPage.getTotalElements(), idPage.getTotalPages(), idPage.getSize(), idPage.getNumber());
        }
        List<Product> products = productRepository.findByIdInWithTagAndParent(ids);
        Map<Long, Integer> order = IntStream.range(0, ids.size()).boxed().collect(Collectors.toMap(ids::get, i -> i));
        products.sort(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), 0)));
        List<ProductResponseDto> content = products.stream().map(ProductMapper::toDto).collect(Collectors.toList());
        return new ProductSearchResultDto(content, idPage.getTotalElements(), idPage.getTotalPages(), idPage.getSize(), idPage.getNumber());
    }

    /**
     * Search & Filter: metin araması, tag filtresi, category path prefix ile sayfalı ürün listesi.
     * @param q Ürün adı veya açıklamada aranacak metin (boş/null = filtre yok)
     * @param tagIds Bu tag'lerden birine ait ürünler (boş/null = filtre yok)
     * @param categoryPathPrefix Tag categoryPath bu prefix ile başlayan ürünler (boş/null = filtre yok)
     * @param pageable Sayfa (page, size)
     */
    public ProductSearchResultDto searchAndFilter(String q, List<Long> tagIds, String categoryPathPrefix, Pageable pageable) {
        List<Long> safeTagIds = (tagIds != null && !tagIds.isEmpty()) ? tagIds : new ArrayList<>();
        Pageable safePageable = pageable != null ? pageable : PageRequest.of(0, 6);
        String qTrimmed = (q != null && !q.isBlank()) ? q.trim() : null;
        String pathPrefix = (categoryPathPrefix != null && !categoryPathPrefix.isBlank()) ? categoryPathPrefix.trim() : null;
        Page<Long> idPage = productRepository.searchProductIds(qTrimmed, safeTagIds, pathPrefix, safePageable);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new ProductSearchResultDto(List.of(), idPage.getTotalElements(), idPage.getTotalPages(), idPage.getSize(), idPage.getNumber());
        }
        List<Product> products = productRepository.findByIdInWithTagAndParent(ids);
        Map<Long, Integer> order = IntStream.range(0, ids.size()).boxed().collect(Collectors.toMap(ids::get, i -> i));
        products.sort(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), 0)));
        List<ProductResponseDto> content = products.stream().map(ProductMapper::toDto).collect(Collectors.toList());
        return new ProductSearchResultDto(content, idPage.getTotalElements(), idPage.getTotalPages(), idPage.getSize(), idPage.getNumber());
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
     */
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
}

