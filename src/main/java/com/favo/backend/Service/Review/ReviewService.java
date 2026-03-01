package com.favo.backend.Service.Review;

import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.*;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    /**
     * Yeni review oluştur
     * Sadece GeneralUser review oluşturabilir
     */
    public ReviewResponseDto createReview(ReviewRequestDto request, SystemUser user) {
        // Kullanıcının GeneralUser olduğunu kontrol et
        if (!(user instanceof GeneralUser)) {
            throw new RuntimeException("Only GeneralUser can create reviews");
        }

        GeneralUser generalUser = (GeneralUser) user;

        // Product kontrolü
        Product product = productRepository.findByIdAndIsActiveTrue(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + request.getProductId()));

        // Rating kontrolü (1-5 arası olmalı)
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        // Review oluştur
        Review review = new Review();
        review.setTitle(request.getTitle());
        review.setDescription(request.getDescription());
        review.setIsCollaborative(request.getIsCollaborative() != null ? request.getIsCollaborative() : false);
        review.setRating(request.getRating());
        review.setProduct(product);
        review.setOwner(generalUser);
        review.setCreatedAt(LocalDateTime.now());
        review.setIsActive(true);

        // Media dosyalarını ekle
        if (request.getMediaList() != null && !request.getMediaList().isEmpty()) {
            List<Media> mediaList = request.getMediaList().stream()
                    .map(mediaDto -> {
                        Media media = new Media();
                        media.setImageData(mediaDto.getImageData());
                        media.setMimeType(mediaDto.getMimeType());
                        media.setReview(review);
                        media.setUploadDate(LocalDateTime.now());
                        media.setIsActive(true);
                        return media;
                    })
                    .collect(Collectors.toList());
            review.setMediaList(mediaList);
        }

        Review saved = reviewRepository.save(review);
        return ReviewMapper.toDto(saved, generalUser.getId());
    }

    /**
     * ID'ye göre review getir
     */
    public ReviewResponseDto getReviewById(Long id, Long currentUserId) {
        Review review = reviewRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));
        return ReviewMapper.toDto(review, currentUserId);
    }

    /**
     * Product'a ait tüm aktif review'ları getir
     */
    public List<ReviewResponseDto> getReviewsByProductId(Long productId, Long currentUserId) {
        List<Review> reviews = reviewRepository.findByProductIdWithRelations(productId);
        return reviews.stream()
                .map(review -> ReviewMapper.toDto(review, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * Kullanıcının tüm review'larını getir
     */
    public List<ReviewResponseDto> getReviewsByUserId(Long userId, Long currentUserId) {
        List<Review> reviews = reviewRepository.findByOwnerIdWithRelations(userId);
        return reviews.stream()
                .map(review -> ReviewMapper.toDto(review, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * Giriş yapmış kullanıcının kendi review'larını getir (My Reviews).
     * En yeni önce sıralı.
     */
    public List<ReviewResponseDto> getMyReviews(SystemUser user) {
        if (user == null) {
            throw new RuntimeException("Authentication required");
        }
        return getReviewsByUserId(user.getId(), user.getId());
    }

    /**
     * Review güncelle
     * Sadece review sahibi güncelleyebilir
     */
    public ReviewResponseDto updateReview(Long id, ReviewRequestDto request, SystemUser user) {
        Review review = reviewRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));

        // Sadece review sahibi güncelleyebilir
        if (!review.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("You can only update your own reviews");
        }

        // Güncelleme
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            review.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            review.setDescription(request.getDescription());
        }
        if (request.getIsCollaborative() != null) {
            review.setIsCollaborative(request.getIsCollaborative());
        }
        if (request.getRating() != null) {
            if (request.getRating() < 1 || request.getRating() > 5) {
                throw new RuntimeException("Rating must be between 1 and 5");
            }
            review.setRating(request.getRating());
        }

        // Media güncelleme (mevcut media'ları sil, yenilerini ekle)
        if (request.getMediaList() != null) {
            // Mevcut media'ları soft delete yap
            if (review.getMediaList() != null) {
                review.getMediaList().forEach(media -> media.setIsActive(false));
            }

            // Yeni media'ları ekle
            List<Media> newMediaList = request.getMediaList().stream()
                    .map(mediaDto -> {
                        Media media = new Media();
                        media.setImageData(mediaDto.getImageData());
                        media.setMimeType(mediaDto.getMimeType());
                        media.setReview(review);
                        media.setUploadDate(LocalDateTime.now());
                        media.setIsActive(true);
                        return media;
                    })
                    .collect(Collectors.toList());
            review.setMediaList(newMediaList);
        }

        Review updated = reviewRepository.save(review);
        return ReviewMapper.toDto(updated, user.getId());
    }

    /**
     * Review'ı sil (soft delete)
     * Sadece review sahibi silebilir
     */
    public void deleteReview(Long id, SystemUser user) {
        Review review = reviewRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));

        // Sadece review sahibi silebilir
        if (!review.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("You can only delete your own reviews");
        }

        // Soft delete
        review.setIsActive(false);
        reviewRepository.save(review);
    }

    /**
     * Product'a ait aktif review'ların ortalama rating'ini hesapla
     * Product bulunamazsa hata fırlatır
     * Review yoksa null döner
     */
    public Double getProductReviewAverageRating(Long productId) {
        // Product'ın varlığını kontrol et
        productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        // Ortalama rating'i hesapla
        return reviewRepository.calculateAverageRatingByProductId(productId);
    }
}

