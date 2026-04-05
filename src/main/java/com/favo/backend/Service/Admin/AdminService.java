package com.favo.backend.Service.Admin;

import com.favo.backend.Domain.admin.AdminPageDto;
import com.favo.backend.Domain.admin.AdminTagDto;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.ProductMapper;
import com.favo.backend.Domain.product.ProductResponseDto;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.favo.backend.Domain.product.Tag;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.ReviewResponseDto;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Service.Review.ReviewService;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserResponseDto;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin paneli: kullanıcı, ürün, tag, review listeleme / detay / aktivasyon.
 * Tüm endpoint'ler RBAC ile sadece ADMIN rolüne açıktır.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final SystemUserRepository systemUserRepository;
    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final UserService userService;
    private final com.favo.backend.Domain.user.UserMapper userMapper;

    // ---- Users ----
    @Transactional(readOnly = true)
    public AdminPageDto<UserResponseDto> listUsers(boolean activeOnly, Pageable pageable) {
        Page<SystemUser> page = activeOnly
                ? systemUserRepository.findActiveWithUserType(pageable)
                : systemUserRepository.findAllWithUserType(pageable);
        List<UserResponseDto> content = page.getContent().stream()
                .map(userMapper::toDtoForList)
                .collect(Collectors.toList());
        return new AdminPageDto<>(content, page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber());
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUser(Long id) {
        SystemUser user = systemUserRepository.findByIdWithUserTypeForAdmin(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return userMapper.toDto(user);
    }

    public void deactivateUser(Long userId) {
        userService.deactivateUser(userId);
    }

    public void activateUser(Long userId) {
        userService.activateUser(userId);
    }

    // ---- Products ----
    @Transactional(readOnly = true)
    public AdminPageDto<ProductResponseDto> listProducts(boolean activeOnly, Pageable pageable) {
        Page<Product> page = activeOnly
                ? productRepository.findActiveWithTag(pageable)
                : productRepository.findAllWithTag(pageable);
        List<ProductResponseDto> content = page.getContent().stream()
                .map(ProductMapper::toDto)
                .collect(Collectors.toList());
        return new AdminPageDto<>(content, page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber());
    }

    @Transactional(readOnly = true)
    public ProductResponseDto getProduct(Long id) {
        Product product = productRepository.findByIdWithTagForAdmin(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        return ProductMapper.toDto(product);
    }

    /** Ürünü arayüzden kaldırır (soft delete). Sistemde kalır, isActive = false. */
    public void deactivateProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        product.setIsActive(false);
        productRepository.save(product);
    }

    /** Ürünü tekrar arayüzde gösterir (soft delete geri alınır). */
    public void activateProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        product.setIsActive(true);
        productRepository.save(product);
    }

    // ---- Tags ----
    @Transactional(readOnly = true)
    public AdminPageDto<AdminTagDto> listTags(boolean activeOnly, Pageable pageable) {
        Page<Tag> page = activeOnly
                ? tagRepository.findByIsActiveTrueOrderByIdAsc(pageable)
                : tagRepository.findAllByOrderByIdAsc(pageable);
        List<AdminTagDto> content = page.getContent().stream()
                .map(this::toAdminTagDto)
                .collect(Collectors.toList());
        return new AdminPageDto<>(content, page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber());
    }

    @Transactional(readOnly = true)
    public AdminTagDto getTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + id));
        return toAdminTagDto(tag);
    }

    private AdminTagDto toAdminTagDto(Tag tag) {
        return new AdminTagDto(
                tag.getId(),
                tag.getName(),
                tag.getCategoryPath(),
                tag.getParent() != null ? tag.getParent().getId() : null,
                tag.getIsActive()
        );
    }

    // ---- Reviews ----
    @Transactional(readOnly = true)
    public AdminPageDto<ReviewResponseDto> listReviews(boolean activeOnly, Pageable pageable) {
        Page<Review> page = activeOnly
                ? reviewRepository.findActiveWithRelations(pageable)
                : reviewRepository.findAllWithRelations(pageable);
        List<ReviewResponseDto> content = reviewService.toResponseDtos(page.getContent(), null);
        return new AdminPageDto<>(content, page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber());
    }

    @Transactional(readOnly = true)
    public ReviewResponseDto getReview(Long id) {
        Review review = reviewRepository.findByIdWithRelationsForAdmin(id)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));
        return reviewService.toResponseDto(review, null);
    }

    public void deactivateReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));
        review.setIsActive(false);
        reviewRepository.save(review);
    }

    public void activateReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));
        review.setIsActive(true);
        reviewRepository.save(review);
    }
}
