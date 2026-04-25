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

    private final HuggingFaceService huggingFaceService;
    private final ReviewRepository reviewRepository;
    private final ReviewFlagRepository reviewFlagRepository;

    @Async
    @Transactional
    public void analyzeAndApplyAsync(Long reviewId) {
        log.info("Toxicity analyze triggered for reviewId={}", reviewId);
        reviewRepository.findById(reviewId).ifPresent(this::analyzeAndApply);
    }

    @Transactional
    public void analyzeAndApply(Review review) {
        // Title ve description'ı birleştirerek analiz et.
        // Kullanıcı sadece title girmiş olabilir; description null ise sadece title kullanılır.
        String title = review.getTitle() != null ? review.getTitle().trim() : "";
        String description = review.getDescription() != null ? review.getDescription().trim() : "";
        String textToAnalyze = description.isEmpty() ? title : (title + " " + description).trim();

        ToxicityResultDto result = huggingFaceService.analyze(textToAnalyze);
        LocalDateTime now = LocalDateTime.now();

        review.setToxicityScore(result.getToxicScore());
        review.setToxicityCheckedAt(now);

        if (result.isToxic()) {
            review.setAutoFlagged(true);
            review.setModerationStatus(ModerationStatus.AUTO_FLAGGED);
            review.setIsActive(false);
            createAiFlag(review, now, result.getToxicScore());
        } else {
            review.setModerationStatus(ModerationStatus.APPROVED);
        }

        reviewRepository.save(review);
    }

    private void createAiFlag(Review review, LocalDateTime now, Double toxicityScore) {
        ReviewFlag flag = new ReviewFlag();
        flag.setReview(review);
        flag.setReportedBy(null);
        flag.setReason(FlagReason.TOXIC_LANGUAGE);
        flag.setNotes("Auto-flagged by HuggingFace toxic-bert. Score: " + toxicityScore);
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

}

