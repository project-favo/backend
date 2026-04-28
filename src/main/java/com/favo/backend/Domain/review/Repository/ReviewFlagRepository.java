package com.favo.backend.Domain.review.Repository;

import com.favo.backend.Domain.review.ModerationStatus;
import com.favo.backend.Domain.review.ReviewFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewFlagRepository extends JpaRepository<ReviewFlag, Long> {

    List<ReviewFlag> findByReviewIdAndIsActiveTrue(Long reviewId);

    Page<ReviewFlag> findByIsActiveTrueAndReview_ModerationStatus(ModerationStatus status, Pageable pageable);

    Long countByIsActiveTrueAndReview_ModerationStatus(ModerationStatus status);

    boolean existsByReviewIdAndReportedByIdAndIsActiveTrue(Long reviewId, Long reportedById);
}

