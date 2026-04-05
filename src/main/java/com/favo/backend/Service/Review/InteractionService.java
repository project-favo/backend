package com.favo.backend.Service.Review;

import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.interaction.Repository.ReviewInteractionRepository;
import com.favo.backend.Domain.interaction.ReviewInteraction;
import com.favo.backend.Domain.product.ProductMapper;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.ProductResponseDto;
import com.favo.backend.Domain.product.ProductSearchResultDto;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Notification.AppNotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InteractionService {

    private final ReviewInteractionRepository reviewInteractionRepository;
    private final ProductInteractionRepository productInteractionRepository;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final AppNotificationService appNotificationService;

    /**
     * Review'a like/unlike yap
     * Eğer zaten like varsa (aktif veya pasif) unlike yapar (soft delete), yoksa like ekler
     * Soft delete edilmiş kayıt varsa onu aktif eder (duplicate kayıt oluşmasını önler)
     */
    public boolean toggleReviewLike(Long reviewId, SystemUser user) {
        // Kullanıcının GeneralUser olduğunu kontrol et
        if (!(user instanceof GeneralUser)) {
            throw new RuntimeException("Only GeneralUser can like reviews");
        }

        GeneralUser generalUser = (GeneralUser) user;

        // Review kontrolü
        Review review = reviewRepository.findByIdAndIsActiveTrue(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));

        // Kendi review'ını beğenemez
        if (review.getOwner().getId().equals(generalUser.getId())) {
            throw new RuntimeException("You cannot like your own review");
        }

        // Mevcut like'ı kontrol et (isActive kontrolü yapmadan - hem aktif hem pasif kayıtları bulur)
        var existingLike = reviewInteractionRepository.findByPerformerIdAndReviewIdAndTypeIgnoreActive(
                generalUser.getId(),
                reviewId,
                "LIKE"
        );

        if (existingLike.isPresent()) {
            ReviewInteraction like = existingLike.get();
            if (like.getIsActive()) {
                // Unlike: Mevcut aktif like'ı soft delete yap
                like.setIsActive(false);
                reviewInteractionRepository.save(like);
                return false; // Unlike yapıldı
            } else {
                // Like: Mevcut pasif like'ı tekrar aktif et (duplicate kayıt oluşturma)
                like.setIsActive(true);
                // createdAt'i güncelleme - bu ilk oluşturulma zamanını korur
                reviewInteractionRepository.save(like);
                notifyReviewLikedSafe(generalUser, review);
                return true; // Like yapıldı
            }
        } else {
            // Like: Yeni like oluştur
            ReviewInteraction like = new ReviewInteraction();
            like.setPerformer(generalUser);
            like.setTargetReview(review);
            like.setType("LIKE");
            like.setCreatedAt(LocalDateTime.now());
            like.setIsActive(true);
            like.recordInteraction();
            reviewInteractionRepository.save(like);
            notifyReviewLikedSafe(generalUser, review);
            return true; // Like yapıldı
        }
    }

    private void notifyReviewLikedSafe(GeneralUser liker, Review review) {
        try {
            appNotificationService.onReviewLiked(liker, review);
        } catch (Exception ignored) {
            // Bildirim başarısız olsa etkileşim tamam
        }
    }

    /**
     * Product'a like/unlike yap
     * Eğer zaten like varsa (aktif veya pasif) unlike yapar (soft delete), yoksa like ekler
     * Soft delete edilmiş kayıt varsa onu aktif eder (duplicate kayıt oluşmasını önler)
     */
    public boolean toggleProductLike(Long productId, SystemUser user) {
        // Kullanıcının GeneralUser olduğunu kontrol et
        if (!(user instanceof GeneralUser)) {
            throw new RuntimeException("Only GeneralUser can like products");
        }

        GeneralUser generalUser = (GeneralUser) user;

        // Product kontrolü
        Product product = productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        // Mevcut like'ı kontrol et (isActive kontrolü yapmadan - hem aktif hem pasif kayıtları bulur)
        var existingLike = productInteractionRepository.findByPerformerIdAndProductIdAndTypeIgnoreActive(
                generalUser.getId(),
                productId,
                "LIKE"
        );

        if (existingLike.isPresent()) {
            ProductInteraction like = existingLike.get();
            if (like.getIsActive()) {
                // Unlike: Mevcut aktif like'ı soft delete yap
                like.setIsActive(false);
                productInteractionRepository.save(like);
                return false; // Unlike yapıldı
            } else {
                // Like: Mevcut pasif like'ı tekrar aktif et (duplicate kayıt oluşturma)
                like.setIsActive(true);
                // createdAt'i güncelleme - bu ilk oluşturulma zamanını korur
                productInteractionRepository.save(like);
                return true; // Like yapıldı
            }
        } else {
            // Like: Yeni like oluştur
            ProductInteraction like = new ProductInteraction();
            like.setPerformer(generalUser);
            like.setTargetProduct(product);
            like.setType("LIKE");
            like.setCreatedAt(LocalDateTime.now());
            like.setIsActive(true);
            like.recordInteraction();
            productInteractionRepository.save(like);
            return true; // Like yapıldı
        }
    }

    /**
     * Review'ın like sayısını getir
     */
    public Long getReviewLikeCount(Long reviewId) {
        return reviewInteractionRepository.countByReviewIdAndType(reviewId, "LIKE");
    }

    /**
     * Review'a yapılan belirli type'taki interaction sayısını getir
     */
    public Long getReviewInteractionCountByType(Long reviewId, String type) {
        return reviewInteractionRepository.countByReviewIdAndType(reviewId, type);
    }

    /**
     * Product'ın like sayısını getir
     */
    public Long getProductLikeCount(Long productId) {
        return productInteractionRepository.countByProductIdAndType(productId, "LIKE");
    }

    /**
     * Product'a yapılan belirli type'taki interaction sayısını getir
     */
    public Long getProductInteractionCountByType(Long productId, String type) {
        return productInteractionRepository.countByProductIdAndType(productId, type);
    }

    /**
     * Product'a rating ver (1-5 arası)
     * Eğer kullanıcı daha önce rating vermişse günceller
     */
    public Integer rateProduct(Long productId, Integer rating, SystemUser user) {
        // Kullanıcının GeneralUser olduğunu kontrol et
        if (!(user instanceof GeneralUser)) {
            throw new RuntimeException("Only GeneralUser can rate products");
        }

        // Rating kontrolü (1-5 arası olmalı)
        if (rating == null || rating < 1 || rating > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        GeneralUser generalUser = (GeneralUser) user;

        // Product kontrolü
        Product product = productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        // Mevcut rating'i kontrol et
        var existingRating = productInteractionRepository.findRatingByPerformerIdAndProductId(
                generalUser.getId(),
                productId
        );

        if (existingRating.isPresent()) {
            // Rating güncelle
            ProductInteraction ratingInteraction = existingRating.get();
            ratingInteraction.setRating(rating);
            productInteractionRepository.save(ratingInteraction);
        } else {
            // Yeni rating oluştur
            ProductInteraction ratingInteraction = new ProductInteraction();
            ratingInteraction.setPerformer(generalUser);
            ratingInteraction.setTargetProduct(product);
            ratingInteraction.setType("RATING");
            ratingInteraction.setRating(rating);
            ratingInteraction.setCreatedAt(LocalDateTime.now());
            ratingInteraction.setIsActive(true);
            ratingInteraction.recordInteraction();
            productInteractionRepository.save(ratingInteraction);
        }

        return rating;
    }

    /**
     * Product'ın ortalama rating'ini getir
     */
    public Double getProductAverageRating(Long productId) {
        Double average = productInteractionRepository.calculateAverageRating(productId);
        return average != null ? average : 0.0;
    }

    /**
     * Kullanıcının product'a verdiği rating'i getir
     */
    public Integer getUserProductRating(Long productId, Long userId) {
        if (userId == null) {
            return null;
        }
        return productInteractionRepository.findRatingByPerformerIdAndProductId(userId, productId)
                .map(ProductInteraction::getRating)
                .orElse(null);
    }

    /**
     * Kullanıcının review'ı beğenip beğenmediğini kontrol et
     */
    public boolean isReviewLikedByUser(Long reviewId, Long userId) {
        if (userId == null) {
            log.debug("isReviewLikedByUser: userId is null for reviewId: {}", reviewId);
            return false;
        }
        log.debug("isReviewLikedByUser: Checking like for reviewId: {}, userId: {}", reviewId, userId);
        var like = reviewInteractionRepository.findByPerformerIdAndReviewIdAndType(
                userId,
                reviewId,
                "LIKE"
        );
        boolean isLiked = like.isPresent();
        log.debug("isReviewLikedByUser: reviewId: {}, userId: {}, isLiked: {}", reviewId, userId, isLiked);
        if (like.isPresent()) {
            log.debug("isReviewLikedByUser: Found like - isActive: {}", like.get().getIsActive());
        }
        return isLiked;
    }

    /**
     * Kullanıcının product'ı beğenip beğenmediğini kontrol et
     */
    public boolean isProductLikedByUser(Long productId, Long userId) {
        if (userId == null) {
            log.debug("isProductLikedByUser: userId is null for productId: {}", productId);
            return false;
        }
        log.debug("isProductLikedByUser: Checking like for productId: {}, userId: {}", productId, userId);
        var like = productInteractionRepository.findByPerformerIdAndProductIdAndType(
                userId,
                productId,
                "LIKE"
        );
        boolean isLiked = like.isPresent();
        log.debug("isProductLikedByUser: productId: {}, userId: {}, isLiked: {}", productId, userId, isLiked);
        if (like.isPresent()) {
            log.debug("isProductLikedByUser: Found like - isActive: {}", like.get().getIsActive());
        }
        return isLiked;
    }

    /**
     * Kullanıcının wishlist'ini (beğendiği ürünler) sayfalı getirir.
     * Beğeni = wishlist; like edilen ürünler listelenir (en son beğenilen önce).
     */
    public ProductSearchResultDto getWishlist(Long userId, Pageable pageable) {
        if (userId == null) {
            return new ProductSearchResultDto(
                    Collections.emptyList(),
                    0L,
                    0,
                    pageable.getPageSize(),
                    pageable.getPageNumber()
            );
        }
        Page<ProductInteraction> page = productInteractionRepository.findLikedProductsByPerformerId(userId, pageable);
        List<ProductResponseDto> content = page.getContent().stream()
                .map(pi -> ProductMapper.toDto(pi.getTargetProduct(), pi.getCreatedAt()))
                .collect(Collectors.toList());
        return new ProductSearchResultDto(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }
}

