package com.favo.backend.Service.Review;

import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.*;
import com.favo.backend.Domain.review.Repository.ReviewFlagRepository;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.common.error.AuthErrorCode;
import com.favo.backend.common.error.FavoException;
import com.favo.backend.common.error.ReviewErrorCode;
import com.favo.backend.Domain.review.Repository.TopReviewerProjection;
import com.favo.backend.Domain.user.Repository.FolloweeFollowerCountProjection;
import com.favo.backend.Domain.user.Repository.UserFollowRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Moderation.ToxicityService;
import com.favo.backend.Service.Notification.AppNotificationService;
import com.favo.backend.Service.User.ProfileImageUrlService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ToxicityService toxicityService;
    private final ReviewFlagRepository reviewFlagRepository;
    private final UserFollowRepository userFollowRepository;
    private final ProfileImageUrlService profileImageUrlService;
    private final AppNotificationService appNotificationService;

    /**
     * Yeni review oluştur
     * Sadece GeneralUser review oluşturabilir
     */
    public ReviewResponseDto createReview(ReviewRequestDto request, SystemUser user) {
        if (user == null) {
            throw new FavoException(AuthErrorCode.AUTH_TOKEN_FIREBASE_INVALID);
        }

        // Kullanıcının GeneralUser olduğunu kontrol et
        if (!(user instanceof GeneralUser)) {
            throw new FavoException(AuthErrorCode.AUTH_FORBIDDEN_ROLE);
        }

        GeneralUser generalUser = (GeneralUser) user;

        // Product kontrolü
        Product product = productRepository.findByIdAndIsActiveTrue(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + request.getProductId()));

        // Rating kontrolü (1-5 arası olmalı)
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        // Kullanıcı bu ürüne daha önce yorum yaptıysa engelle
        if (reviewRepository.existsByOwnerIdAndProductIdAndIsActiveTrue(
                generalUser.getId(), product.getId())) {
            throw new FavoException(ReviewErrorCode.REVIEW_DUPLICATE_FOR_PRODUCT,
                    java.util.Map.of("userId", generalUser.getId(), "productId", product.getId()));
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
        toxicityService.analyzeAndApplyAsync(saved.getId());
        try {
            appNotificationService.onNewReviewOnProduct(saved);
        } catch (Exception ignored) {
            // Bildirim hatası review oluşturmayı bozmasın
        }
        return toResponseDto(saved, generalUser.getId());
    }

    public FlagResponseDto flagReview(Long reviewId, GeneralUser user, FlagRequestDto request) {
        Review review = reviewRepository.findByIdAndIsActiveTrue(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));

        if (reviewFlagRepository.existsByReviewIdAndReportedByIdAndIsActiveTrue(reviewId, user.getId())) {
            throw new RuntimeException("You have already flagged this review");
        }

        review.setModerationStatus(ModerationStatus.MANUALLY_FLAGGED);

        ReviewFlag flag = new ReviewFlag();
        flag.setReview(review);
        flag.setReportedBy(user);
        flag.setReason(request.getReason());
        flag.setNotes(request.getNotes());
        flag.setCreatedAt(LocalDateTime.now());
        flag.setIsActive(true);

        ReviewFlag saved = reviewFlagRepository.save(flag);
        return new FlagResponseDto(
                saved.getId(),
                reviewId,
                saved.getReason(),
                saved.getNotes(),
                saved.getCreatedAt()
        );
    }

    /**
     * ID'ye göre review getir
     */
    public ReviewResponseDto getReviewById(Long id, Long currentUserId) {
        Review review = reviewRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));
        return toResponseDto(review, currentUserId);
    }

    /**
     * Admin veya toplu dönüşüm: review sahibi için {@code ownerProfilePhotoUrl} doldurulur.
     */
    public ReviewResponseDto toResponseDto(Review review, Long currentUserId) {
        return ReviewMapper.toDto(review, currentUserId, ownerProfilePhotoUrl(review));
    }

    public List<ReviewResponseDto> toResponseDtos(List<Review> reviews, Long currentUserId) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        return reviews.stream()
                .map(r -> ReviewMapper.toDto(r, currentUserId, ownerProfilePhotoUrl(r)))
                .collect(Collectors.toList());
    }

    private String ownerProfilePhotoUrl(Review review) {
        Long ownerId = review.getOwner() != null ? review.getOwner().getId() : null;
        return ownerId != null ? profileImageUrlService.buildProfileImageUrl(ownerId) : null;
    }

    /**
     * Product'a ait tüm aktif review'ları getir
     */
    public List<ReviewResponseDto> getReviewsByProductId(Long productId, Long currentUserId) {
        List<Review> reviews = reviewRepository.findByProductIdWithRelations(productId);
        return toResponseDtos(reviews, currentUserId);
    }

    public List<ReviewResponseDto> getReviewsByProductId(
            Long productId,
            Long currentUserId,
            Boolean hasMedia,
            Boolean isCollaborative,
            String sort
    ) {
        List<Review> reviews = reviewRepository.findByProductIdWithRelations(productId);

        if (isCollaborative != null) {
            reviews = reviews.stream()
                    .filter(r -> Objects.equals(r.getIsCollaborative(), isCollaborative))
                    .collect(Collectors.toList());
        }

        if (hasMedia != null) {
            reviews = reviews.stream()
                    .filter(r -> {
                        boolean reviewHasMedia = r.getMediaList() != null && r.getMediaList().stream()
                                .anyMatch(m -> Boolean.TRUE.equals(m.getIsActive()));
                        return hasMedia.equals(reviewHasMedia);
                    })
                    .collect(Collectors.toList());
        }

        List<ReviewResponseDto> dtos = toResponseDtos(reviews, currentUserId);
        SortOption sortOption = SortOption.from(sort);
        if (sortOption == SortOption.NEWEST) {
            dtos.sort(Comparator.comparing(ReviewResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            return dtos;
        }
        if (sortOption == SortOption.MOST_LIKED) {
            dtos.sort(Comparator
                    .comparing(ReviewResponseDto::getLikeCount, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                    .thenComparing(ReviewResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return dtos;
        }
        if (sortOption == SortOption.HIGHEST_RATING) {
            dtos.sort(Comparator
                    .comparing(ReviewResponseDto::getRating, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                    .thenComparing(ReviewResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return dtos;
        }
        if (sortOption == SortOption.LOWEST_RATING) {
            dtos.sort(Comparator
                    .comparing(ReviewResponseDto::getRating, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ReviewResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return dtos;
        }

        Map<Long, Long> followerCountByUserId = followerCountByUserId(dtos);
        dtos.sort(Comparator
                .comparing((ReviewResponseDto dto) -> followerCountByUserId.getOrDefault(dto.getOwnerId(), 0L))
                .reversed()
                .thenComparing(ReviewResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return dtos;
    }

    private Map<Long, Long> followerCountByUserId(List<ReviewResponseDto> dtos) {
        List<Long> ownerIds = dtos.stream()
                .map(ReviewResponseDto::getOwnerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ownerIds.isEmpty()) {
            return Map.of();
        }

        List<FolloweeFollowerCountProjection> rows = userFollowRepository.countActiveFollowersByFolloweeIds(ownerIds);
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(row.getFolloweeId(), row.getFollowerCount()));
        return counts;
    }

    private enum SortOption {
        NEWEST,
        MOST_LIKED,
        HIGHEST_RATING,
        LOWEST_RATING,
        TOP_FOLLOWER_AUTHOR;

        static SortOption from(String rawSort) {
            if (rawSort == null || rawSort.isBlank()) {
                return NEWEST;
            }
            String normalized = rawSort.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "most_liked" -> MOST_LIKED;
                case "highest_rating" -> HIGHEST_RATING;
                case "lowest_rating" -> LOWEST_RATING;
                case "top_follower_author" -> TOP_FOLLOWER_AUTHOR;
                case "newest" -> NEWEST;
                default -> NEWEST;
            };
        }
    }

    /**
     * Kullanıcının tüm review'larını getir
     */
    public List<ReviewResponseDto> getReviewsByUserId(Long userId, Long currentUserId) {
        List<Review> reviews = reviewRepository.findByOwnerIdWithRelations(userId);
        return toResponseDtos(reviews, currentUserId);
    }

    /**
     * Giriş yapmış kullanıcının kendi review'larını getir (My Reviews) — sayfalı.
     * Varsayılan sıra: {@code createdAt} azalan.
     */
    public Page<ReviewResponseDto> getMyReviews(SystemUser user, Pageable pageable) {
        if (user == null) {
            throw new RuntimeException("Authentication required");
        }
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 50);
        int pageNumber = Math.max(pageable.getPageNumber(), 0);
        Pageable sanitized = PageRequest.of(pageNumber, size, pageable.getSort());
        Page<Review> page = reviewRepository.findByOwnerIdWithRelationsPage(user.getId(), sanitized);
        List<ReviewResponseDto> content = toResponseDtos(page.getContent(), user.getId());
        return new PageImpl<>(content, sanitized, page.getTotalElements());
    }

    /**
     * Kullanıcının tüm aktif yorumlarının ortalama yıldızı (My Reviews profil metrikleri).
     * Yorum yoksa {@code null}.
     */
    public Double getMyReviewsAverageRating(SystemUser user) {
        if (user == null) {
            throw new RuntimeException("Authentication required");
        }
        return reviewRepository.calculateAverageRatingByOwnerId(user.getId());
    }

    /**
     * En çok review yapan kullanıcıları getirir.
     * Yalnızca aktif review'lar ve aktif kullanıcılar sayılır.
     */
    public List<TopReviewerResponseDto> getTopReviewers(Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 50);
        List<TopReviewerProjection> rows = reviewRepository.findTopReviewers(PageRequest.of(0, safeLimit));
        return rows.stream()
                .map(row -> new TopReviewerResponseDto(
                        row.getUserId(),
                        row.getUserName(),
                        profileImageUrlService.buildProfileImageUrl(row.getUserId()),
                        row.getReviewCount()
                ))
                .collect(Collectors.toList());
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

        // Güncelleme — metin değişikliği varsa toxicity yeniden çalışacak
        boolean textChanged = false;
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            if (!request.getTitle().equals(review.getTitle())) textChanged = true;
            review.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            if (!request.getDescription().equals(review.getDescription())) textChanged = true;
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

        // Metin güncellendiyse moderation durumunu sıfırla; toxicity check kaydedildikten sonra async tetiklenir.
        if (textChanged) {
            review.setModerationStatus(ModerationStatus.PENDING);
            review.setAutoFlagged(false);
            review.setToxicityScore(null);
            review.setToxicityCheckedAt(null);
            review.setIsActive(true);
        }

        // Media güncelleme: retain (koru) + upload (yeni yükle) ayrımı yapılır.
        if (request.getMediaList() != null) {
            List<MediaRequestDto> mediaRequests = request.getMediaList();

            // Korunacak mevcut media ID'leri topla.
            Set<Long> retainIds = mediaRequests.stream()
                    .filter(MediaRequestDto::isRetain)
                    .map(MediaRequestDto::getExistingMediaIdAsLong)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Retain listesinde olmayan mevcut media'ları soft delete et.
            if (review.getMediaList() != null) {
                review.getMediaList().forEach(media -> {
                    if (!retainIds.contains(media.getId())) {
                        media.setIsActive(false);
                    }
                });
            }

            // Yeni yüklenecek media'ları oluştur.
            List<Media> newUploads = mediaRequests.stream()
                    .filter(dto -> !dto.isRetain() && dto.getImageData() != null && dto.getImageData().length > 0)
                    .map(dto -> {
                        Media media = new Media();
                        media.setImageData(dto.getImageData());
                        media.setMimeType(dto.getMimeType() != null ? dto.getMimeType() : "image/jpeg");
                        media.setReview(review);
                        media.setUploadDate(LocalDateTime.now());
                        media.setIsActive(true);
                        return media;
                    })
                    .collect(Collectors.toList());

            // Nihai liste: korunan mevcutlar + yeni yüklemeler (request sırasına göre).
            List<Media> finalMediaList = new ArrayList<>();
            for (MediaRequestDto dto : mediaRequests) {
                if (dto.isRetain()) {
                    Long retainId = dto.getExistingMediaIdAsLong();
                    if (retainId == null) continue;
                    review.getMediaList().stream()
                            .filter(m -> m.getId().equals(retainId) && Boolean.TRUE.equals(m.getIsActive()))
                            .findFirst()
                            .ifPresent(finalMediaList::add);
                }
            }
            finalMediaList.addAll(newUploads);
            review.setMediaList(finalMediaList);
        }

        Review updated = reviewRepository.save(review);
        if (textChanged) {
            toxicityService.analyzeAndApplyAsync(updated.getId());
        }
        return toResponseDto(updated, user.getId());
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

