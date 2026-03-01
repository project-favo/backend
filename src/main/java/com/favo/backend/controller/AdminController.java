package com.favo.backend.controller;

import com.favo.backend.Domain.admin.AdminPageDto;
import com.favo.backend.Domain.admin.AdminTagDto;
import com.favo.backend.Domain.product.ProductResponseDto;
import com.favo.backend.Domain.review.ReviewResponseDto;
import com.favo.backend.Domain.user.UserResponseDto;
import com.favo.backend.Service.Admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin paneli API. Tüm endpoint'ler sadece ROLE_ADMIN ile erişilebilir.
 * SecurityConfig'de /api/admin/** hasRole("ADMIN") ile korunur.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    // ---- Users ----
    /**
     * GET /api/admin/users?page=0&size=20&activeOnly=false
     * activeOnly=true ise sadece aktif kullanıcılar (varsayılan: false = hepsi)
     */
    @GetMapping("/users")
    public ResponseEntity<AdminPageDto<UserResponseDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(adminService.listUsers(activeOnly, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        adminService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Products ----
    /**
     * GET /api/admin/products?page=0&size=20&activeOnly=false
     */
    @GetMapping("/products")
    public ResponseEntity<AdminPageDto<ProductResponseDto>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(adminService.listProducts(activeOnly, pageable));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getProduct(id));
    }

    // ---- Tags ----
    /**
     * GET /api/admin/tags?page=0&size=20&activeOnly=false
     */
    @GetMapping("/tags")
    public ResponseEntity<AdminPageDto<AdminTagDto>> listTags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(adminService.listTags(activeOnly, pageable));
    }

    @GetMapping("/tags/{id}")
    public ResponseEntity<AdminTagDto> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getTag(id));
    }

    // ---- Reviews ----
    /**
     * GET /api/admin/reviews?page=0&size=20&activeOnly=false
     */
    @GetMapping("/reviews")
    public ResponseEntity<AdminPageDto<ReviewResponseDto>> listReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(adminService.listReviews(activeOnly, pageable));
    }

    @GetMapping("/reviews/{id}")
    public ResponseEntity<ReviewResponseDto> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getReview(id));
    }

    @PatchMapping("/reviews/{id}/deactivate")
    public ResponseEntity<Void> deactivateReview(@PathVariable Long id) {
        adminService.deactivateReview(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reviews/{id}/activate")
    public ResponseEntity<Void> activateReview(@PathVariable Long id) {
        adminService.activateReview(id);
        return ResponseEntity.noContent().build();
    }
}
