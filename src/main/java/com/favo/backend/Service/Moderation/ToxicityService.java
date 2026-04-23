package com.favo.backend.Service.Moderation;

import com.favo.backend.Domain.review.*;
import com.favo.backend.Domain.review.Repository.ReviewFlagRepository;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToxicityService {

    private final OpenAiModerationService openAiModerationService;
    private final ReviewRepository reviewRepository;
    private final ReviewFlagRepository reviewFlagRepository;

    @Async
    @Transactional
    public void analyzeAndApplyAsync(Long reviewId) {
        reviewRepository.findById(reviewId).ifPresent(this::analyzeAndApply);
    }

    @Transactional
    public void analyzeAndApply(Review review) {
        ToxicityResultDto result = openAiModerationService.analyze(review.getDescription());
        LocalDateTime now = LocalDateTime.now();

        review.setToxicityScore(result.getToxicScore());
        review.setToxicityCheckedAt(now);

        Double score = result.getToxicScore();
        if (score == null) {
            review.setModerationStatus(ModerationStatus.APPROVED);
            reviewRepository.save(review);
            return;
        }

        if (score >= 0.80) {
            review.setAutoFlagged(true);
            review.setModerationStatus(ModerationStatus.AUTO_FLAGGED);
            review.setIsActive(false);
            createAiFlag(review, score, now);
        } else if (score >= 0.50) {
            review.setAutoFlagged(true);
            review.setModerationStatus(ModerationStatus.AUTO_FLAGGED);
            createAiFlag(review, score, now);
        } else {
            review.setModerationStatus(ModerationStatus.APPROVED);
        }

        reviewRepository.save(review);
    }

    private void createAiFlag(Review review, Double score, LocalDateTime now) {
        ReviewFlag flag = new ReviewFlag();
        flag.setReview(review);
        flag.setReportedBy(null);
        flag.setReason(FlagReason.TOXIC_LANGUAGE);
        flag.setNotes("Auto-flagged by AI. Score: " + score);
        flag.setCreatedAt(now);
        flag.setIsActive(true);
        reviewFlagRepository.save(flag);
    }

    @Transactional
    public void reanalyzeReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));
        analyzeAndApply(review);
    }

    public void assertNotFlagged(String text) {
        openAiModerationService.analyze(text);
    }
}

