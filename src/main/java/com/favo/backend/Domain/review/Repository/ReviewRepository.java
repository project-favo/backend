package com.favo.backend.Domain.review.Repository;

import com.favo.backend.Domain.review.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdAndIsActiveTrue(Long productId);
    List<Review> findByOwnerIdAndIsActiveTrue(Long ownerId);
    Optional<Review> findByIdAndIsActiveTrue(Long id);
    List<Review> findByIsActiveTrue();
}

