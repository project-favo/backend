package com.favo.backend.Service.Review;

import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.interaction.Repository.ReviewInteractionRepository;
import com.favo.backend.Domain.interaction.ReviewInteraction;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class InteractionService {

    private final ReviewInteractionRepository reviewInteractionRepository;
    private final ProductInteractionRepository productInteractionRepository;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    /**
     * Review'a like/unlike yap
     * Eğer zaten like varsa unlike yapar (soft delete), yoksa like ekler
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

        // Mevcut like'ı kontrol et
        var existingLike = reviewInteractionRepository.findByPerformerIdAndReviewIdAndType(
                generalUser.getId(),
                reviewId,
                "LIKE"
        );

        if (existingLike.isPresent()) {
            // Unlike: Mevcut like'ı soft delete yap
            ReviewInteraction like = existingLike.get();
            like.setIsActive(false);
            reviewInteractionRepository.save(like);
            return false; // Unlike yapıldı
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
            return true; // Like yapıldı
        }
    }

    /**
     * Product'a like/unlike yap
     * Eğer zaten like varsa unlike yapar (soft delete), yoksa like ekler
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

        // Mevcut like'ı kontrol et
        var existingLike = productInteractionRepository.findByPerformerIdAndProductIdAndType(
                generalUser.getId(),
                productId,
                "LIKE"
        );

        if (existingLike.isPresent()) {
            // Unlike: Mevcut like'ı soft delete yap
            ProductInteraction like = existingLike.get();
            like.setIsActive(false);
            productInteractionRepository.save(like);
            return false; // Unlike yapıldı
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
     * Product'ın like sayısını getir
     */
    public Long getProductLikeCount(Long productId) {
        return productInteractionRepository.countByProductIdAndType(productId, "LIKE");
    }

    /**
     * Kullanıcının review'ı beğenip beğenmediğini kontrol et
     */
    public boolean isReviewLikedByUser(Long reviewId, Long userId) {
        if (userId == null) {
            return false;
        }
        return reviewInteractionRepository.findByPerformerIdAndReviewIdAndType(
                userId,
                reviewId,
                "LIKE"
        ).isPresent();
    }

    /**
     * Kullanıcının product'ı beğenip beğenmediğini kontrol et
     */
    public boolean isProductLikedByUser(Long productId, Long userId) {
        if (userId == null) {
            return false;
        }
        return productInteractionRepository.findByPerformerIdAndProductIdAndType(
                userId,
                productId,
                "LIKE"
        ).isPresent();
    }
}

