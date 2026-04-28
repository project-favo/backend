package com.favo.backend.Domain.review.Repository;

import com.favo.backend.Domain.review.ModerationStatus;
import com.favo.backend.Domain.review.ReviewFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewFlagRepository extends JpaRepository<ReviewFlag, Long> {

    List<ReviewFlag> findByReviewIdAndIsActiveTrue(Long reviewId);

    Page<ReviewFlag> findByIsActiveTrueAndReview_ModerationStatus(ModerationStatus status, Pageable pageable);

    Long countByIsActiveTrueAndReview_ModerationStatus(ModerationStatus status);

    boolean existsByReviewIdAndReportedByIdAndIsActiveTrue(Long reviewId, Long reportedById);

    @Query("SELECT rf FROM ReviewFlag rf " +
           "WHERE rf.review.id = :reviewId " +
           "AND rf.reportedBy IS NULL " +
           "AND rf.isActive = true " +
           "AND rf.resolvedAt IS NULL")
    Optional<ReviewFlag> findOpenAiFlagByReviewId(@Param("reviewId") Long reviewId);
}

