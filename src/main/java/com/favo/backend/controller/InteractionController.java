package com.favo.backend.controller;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Review.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Interaction Controller
 * Like/Unlike işlemleri için endpoint'ler
 */
@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    /**
     * ❤️ Review'a like/unlike yap
     * POST /api/interactions/review/{reviewId}/like
     * 
     * Eğer zaten like varsa unlike yapar, yoksa like ekler
     * Kendi review'ınızı beğenemezsiniz
     * 
     * Response: 200 OK + { "liked": true/false }
     * Error: 404 Not Found - Review bulunamazsa
     * Error: 400 Bad Request - Kendi review'ınızı beğenmeye çalışırsanız
     */
    @PostMapping("/review/{reviewId}/like")
    public ResponseEntity<Map<String, Object>> toggleReviewLike(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal SystemUser user
    ) {
        boolean liked = interactionService.toggleReviewLike(reviewId, user);
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    /**
     * ❤️ Product'a like/unlike yap
     * POST /api/interactions/product/{productId}/like
     * 
     * Eğer zaten like varsa unlike yapar, yoksa like ekler
     * 
     * Response: 200 OK + { "liked": true/false }
     * Error: 404 Not Found - Product bulunamazsa
     */
    @PostMapping("/product/{productId}/like")
    public ResponseEntity<Map<String, Object>> toggleProductLike(
            @PathVariable Long productId,
            @AuthenticationPrincipal SystemUser user
    ) {
        boolean liked = interactionService.toggleProductLike(productId, user);
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    /**
     * 📊 Review'ın like sayısını getir
     * GET /api/interactions/review/{reviewId}/like-count
     * 
     * Response: 200 OK + { "count": 123 }
     */
    @GetMapping("/review/{reviewId}/like-count")
    public ResponseEntity<Map<String, Long>> getReviewLikeCount(@PathVariable Long reviewId) {
        Long count = interactionService.getReviewLikeCount(reviewId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 📊 Product'ın like sayısını getir
     * GET /api/interactions/product/{productId}/like-count
     * 
     * Response: 200 OK + { "count": 123 }
     */
    @GetMapping("/product/{productId}/like-count")
    public ResponseEntity<Map<String, Long>> getProductLikeCount(@PathVariable Long productId) {
        Long count = interactionService.getProductLikeCount(productId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * ✅ Kullanıcının review'ı beğenip beğenmediğini kontrol et
     * GET /api/interactions/review/{reviewId}/is-liked
     * 
     * Response: 200 OK + { "isLiked": true/false }
     */
    @GetMapping("/review/{reviewId}/is-liked")
    public ResponseEntity<Map<String, Boolean>> isReviewLiked(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal SystemUser user
    ) {
        Long userId = user != null ? user.getId() : null;
        boolean isLiked = interactionService.isReviewLikedByUser(reviewId, userId);
        return ResponseEntity.ok(Map.of("isLiked", isLiked));
    }

    /**
     * ✅ Kullanıcının product'ı beğenip beğenmediğini kontrol et
     * GET /api/interactions/product/{productId}/is-liked
     * 
     * Response: 200 OK + { "isLiked": true/false }
     */
    @GetMapping("/product/{productId}/is-liked")
    public ResponseEntity<Map<String, Boolean>> isProductLiked(
            @PathVariable Long productId,
            @AuthenticationPrincipal SystemUser user
    ) {
        Long userId = user != null ? user.getId() : null;
        boolean isLiked = interactionService.isProductLikedByUser(productId, userId);
        return ResponseEntity.ok(Map.of("isLiked", isLiked));
    }
}

