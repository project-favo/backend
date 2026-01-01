package com.favo.backend.controller;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Review.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
     * 📊 Review'a yapılan belirli type'taki interaction sayısını getir
     * GET /api/interactions/review/{reviewId}/count?type=LIKE
     * 
     * Query Parameters:
     * - type (String, required): Interaction type (LIKE, DISLIKE, REPORT, vb.)
     * 
     * Response: 200 OK + { "count": 123, "type": "LIKE" }
     */
    @GetMapping("/review/{reviewId}/count")
    public ResponseEntity<Map<String, Object>> getReviewInteractionCountByType(
            @PathVariable Long reviewId,
            @RequestParam String type
    ) {
        Long count = interactionService.getReviewInteractionCountByType(reviewId, type);
        return ResponseEntity.ok(Map.of("count", count, "type", type));
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
     * 📊 Product'a yapılan belirli type'taki interaction sayısını getir
     * GET /api/interactions/product/{productId}/count?type=LIKE
     * 
     * Query Parameters:
     * - type (String, required): Interaction type (LIKE, WISHLIST, RATING, vb.)
     * 
     * Response: 200 OK + { "count": 123, "type": "LIKE" }
     */
    @GetMapping("/product/{productId}/count")
    public ResponseEntity<Map<String, Object>> getProductInteractionCountByType(
            @PathVariable Long productId,
            @RequestParam String type
    ) {
        Long count = interactionService.getProductInteractionCountByType(productId, type);
        return ResponseEntity.ok(Map.of("count", count, "type", type));
    }

    /**
     * ⭐ Product'a rating ver (1-5 arası yıldız sistemi)
     * POST /api/interactions/product/{productId}/rating
     * 
     * Eğer kullanıcı daha önce rating vermişse günceller
     * 
     * Request Body:
     * {
     *   "rating": 4  // 1-5 arası
     * }
     * 
     * Response: 200 OK + { "rating": 4 }
     * Error: 400 Bad Request - Rating 1-5 arası değilse
     * Error: 404 Not Found - Product bulunamazsa
     */
    @PostMapping("/product/{productId}/rating")
    public ResponseEntity<Map<String, Integer>> rateProduct(
            @PathVariable Long productId,
            @RequestBody Map<String, Integer> request,
            @AuthenticationPrincipal SystemUser user
    ) {
        Integer rating = request.get("rating");
        Integer savedRating = interactionService.rateProduct(productId, rating, user);
        return ResponseEntity.ok(Map.of("rating", savedRating));
    }

    /**
     * ⭐ Product'ın ortalama rating'ini getir
     * GET /api/interactions/product/{productId}/average-rating
     * 
     * Response: 200 OK + { "averageRating": 4.5, "productId": 123 }
     */
    @GetMapping("/product/{productId}/average-rating")
    public ResponseEntity<Map<String, Object>> getProductAverageRating(@PathVariable Long productId) {
        Double averageRating = interactionService.getProductAverageRating(productId);
        return ResponseEntity.ok(Map.of("averageRating", averageRating, "productId", productId));
    }

    /**
     * ⭐ Kullanıcının product'a verdiği rating'i getir
     * GET /api/interactions/product/{productId}/user-rating
     * 
     * Response: 200 OK + { "rating": 4 } veya { "rating": null } (rating vermemişse)
     */
    @GetMapping("/product/{productId}/user-rating")
    public ResponseEntity<Map<String, Object>> getUserProductRating(
            @PathVariable Long productId,
            @AuthenticationPrincipal SystemUser user
    ) {
        Long userId = user != null ? user.getId() : null;
        Integer rating = interactionService.getUserProductRating(productId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("rating", rating);
        return ResponseEntity.ok(response);
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

