package com.favo.backend.controller;

import com.favo.backend.Domain.review.FlagRequestDto;
import com.favo.backend.Domain.review.FlagResponseDto;
import com.favo.backend.Domain.review.ReviewRequestDto;
import com.favo.backend.Domain.review.ReviewResponseDto;
import com.favo.backend.Domain.review.TopReviewerResponseDto;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Review Controller
 * Review CRUD işlemleri için endpoint'ler
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 🆕 Yeni review oluştur
     * POST /api/reviews
     * 
     * User bilgisi Authorization header'daki Bearer token'dan alınır (@AuthenticationPrincipal)
     * 
     * Body (mediaList opsiyonel):
     * {
     *   "productId": 123,
     *   "title": "Great product!",
     *   "description": "Really satisfied with this product...",
     *   "isCollaborative": false,
     *   "rating": 5,
     *   "mediaList": [  // Opsiyonel - gönderilmezse review medya olmadan oluşturulur
     *     {
     *       "imageData": [base64 or binary],
     *       "mimeType": "image/jpeg"
     *     }
     *   ]
     * }
     * 
     * Body (mediaList olmadan):
     * {
     *   "productId": 123,
     *   "title": "Great product!",
     *   "description": "Really satisfied with this product...",
     *   "isCollaborative": false,
     *   "rating": 5
     * }
     * 
     * Response: 201 Created + ReviewResponseDto
     * Error: 400 Bad Request - Product bulunamazsa veya rating geçersizse
     */
    @PostMapping
    public ResponseEntity<ReviewResponseDto> createReview(
            @RequestBody ReviewRequestDto request,
            @AuthenticationPrincipal SystemUser user
    ) {
        ReviewResponseDto created = reviewService.createReview(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 👤 Giriş yapmış kullanıcının kendi review'ları (My Reviews)
     * GET /api/reviews/me
     *
     * Token zorunlu. En yeni önce sıralı.
     * Response: 200 OK + List<ReviewResponseDto>
     * Error: 401 Unauthorized - Token yoksa veya geçersizse
     */
    @GetMapping("/me")
    public ResponseEntity<List<ReviewResponseDto>> getMyReviews(
            @AuthenticationPrincipal SystemUser user
    ) {
        List<ReviewResponseDto> reviews = reviewService.getMyReviews(user);
        return ResponseEntity.ok(reviews);
    }

    /**
     * 📋 Tüm aktif review'ları getir
     * GET /api/reviews
     *
     * Response: 200 OK + List<ReviewResponseDto>
     */
    @GetMapping
    public ResponseEntity<List<ReviewResponseDto>> getAllReviews(
            @AuthenticationPrincipal SystemUser user
    ) {
        return ResponseEntity.ok(List.of());
    }

    /**
     * 🔍 ID'ye göre review getir
     * GET /api/reviews/{id}
     * 
     * Response: 200 OK + ReviewResponseDto
     * Error: 404 Not Found - Review bulunamazsa veya pasifse
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponseDto> getReviewById(
            @PathVariable Long id,
            @AuthenticationPrincipal SystemUser user
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        ReviewResponseDto review = reviewService.getReviewById(id, currentUserId);
        return ResponseEntity.ok(review);
    }

    /**
     * 🏷️ Product'a göre review'ları getir
     * GET /api/reviews/product/{productId}
     * 
     * Belirli bir product'a ait tüm aktif review'ları döner
     * 
     * Response: 200 OK + List<ReviewResponseDto>
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ReviewResponseDto>> getReviewsByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Boolean hasMedia,
            @RequestParam(required = false) Boolean isCollaborative,
            @RequestParam(defaultValue = "newest") String sort,
            @AuthenticationPrincipal SystemUser user
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        List<ReviewResponseDto> reviews = reviewService.getReviewsByProductId(
                productId,
                currentUserId,
                hasMedia,
                isCollaborative,
                sort
        );
        return ResponseEntity.ok(reviews);
    }

    /**
     * 👤 Kullanıcıya göre review'ları getir
     * GET /api/reviews/user/{userId}
     * 
     * Belirli bir kullanıcının tüm aktif review'larını döner
     * 
     * Response: 200 OK + List<ReviewResponseDto>
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewResponseDto>> getReviewsByUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal SystemUser user
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        List<ReviewResponseDto> reviews = reviewService.getReviewsByUserId(userId, currentUserId);
        return ResponseEntity.ok(reviews);
    }

    /**
     * En çok review yapan kullanıcıları döner (default: 5).
     * Bu endpoint user-based token gerektirir.
     */
    @GetMapping("/top-reviewers")
    public ResponseEntity<?> getTopReviewers(
            @RequestParam(defaultValue = "5") Integer limit,
            @AuthenticationPrincipal SystemUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required");
        }
        List<TopReviewerResponseDto> topUsers = reviewService.getTopReviewers(limit);
        return ResponseEntity.ok(topUsers);
    }

    /**
     * ✏️ Review güncelle
     * PUT /api/reviews/{id}
     * 
     * Sadece review sahibi güncelleyebilir
     * Partial update: Sadece gönderilen field'lar güncellenir
     * 
     * Body: {
     *   "title": "Updated Title",  // Opsiyonel
     *   "description": "...",      // Opsiyonel
     *   "isCollaborative": true,    // Opsiyonel
     *   "rating": 4,                // Opsiyonel
     *   "mediaList": [...]          // Opsiyonel
     * }
     * 
     * Response: 200 OK + ReviewResponseDto
     * Error: 404 Not Found - Review bulunamazsa
     * Error: 403 Forbidden - Review sahibi değilseniz
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReviewResponseDto> updateReview(
            @PathVariable Long id,
            @RequestBody ReviewRequestDto request,
            @AuthenticationPrincipal SystemUser user
    ) {
        ReviewResponseDto updated = reviewService.updateReview(id, request, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * 🗑️ Review'ı sil (soft delete - isActive = false)
     * DELETE /api/reviews/{id}
     * 
     * Sadece review sahibi silebilir
     * Review fiziksel olarak silinmez, sadece isActive = false yapılır
     * 
     * Response: 204 No Content
     * Error: 404 Not Found - Review bulunamazsa veya zaten pasifse
     * Error: 403 Forbidden - Review sahibi değilseniz
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long id,
            @AuthenticationPrincipal SystemUser user
    ) {
        reviewService.deleteReview(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kullanıcının bir review'u raporlaması (flag).
     * POST /api/reviews/{id}/flag
     */
    @PostMapping("/{id}/flag")
    public ResponseEntity<FlagResponseDto> flagReview(
            @PathVariable Long id,
            @RequestBody FlagRequestDto request,
            @AuthenticationPrincipal SystemUser user
    ) {
        if (!(user instanceof GeneralUser generalUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        FlagResponseDto dto = reviewService.flagReview(id, generalUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}

