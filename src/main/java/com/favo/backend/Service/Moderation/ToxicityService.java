package com.favo.backend.Service.Moderation;

import com.favo.backend.Domain.review.FlagReason;
import com.favo.backend.Domain.review.ModerationStatus;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.ReviewFlag;
import com.favo.backend.Domain.review.Repository.ReviewFlagRepository;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.config.ToxicityConfig;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToxicityService {

    private static final Set<ModerationStatus> HUMAN_LOCKED_STATUSES = Set.of(
            ModerationStatus.MANUALLY_FLAGGED,
            ModerationStatus.REJECTED
    );

    private final HuggingFaceService huggingFaceService;
    private final ReviewRepository reviewRepository;
    private final ReviewFlagRepository reviewFlagRepository;
    private final ToxicityConfig.ToxicityProperties toxicityProperties;

    @Async
    @Transactional
    public void analyzeAndApplyAsync(Long reviewId) {
        log.info("Toxicity analyze triggered for reviewId={}", reviewId);
        reviewRepository.findById(reviewId).ifPresentOrElse(this::analyzeAndApply, () ->
                log.warn("Review not found for toxicity analysis reviewId={}", reviewId));
    }

    @Transactional
    public void analyzeAndApply(Review review) {
        Review latest = reviewRepository.findById(review.getId())
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + review.getId()));

        if (HUMAN_LOCKED_STATUSES.contains(latest.getModerationStatus())) {
            log.info("Skipping AI moderation due to human-locked status. reviewId={}, status={}",
                    latest.getId(), latest.getModerationStatus());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        latest.setToxicityCheckedAt(now);

        try {
            Map<String, Double> labelScores = huggingFaceService.analyzeLabelScores(latest.getDescription());
            Decision decision = decide(labelScores);
            latest.setToxicityScore(decision.maxScore());

            switch (decision.riskLevel()) {
                case HIGH -> {
                    latest.setAutoFlagged(true);
                    latest.setModerationStatus(ModerationStatus.AUTO_FLAGGED);
                    latest.setIsActive(false);
                    upsertAiFlag(latest, now, decision.note());
                }
                case BORDERLINE -> {
                    latest.setAutoFlagged(true);
                    latest.setModerationStatus(ModerationStatus.REVIEW_REQUIRED);
                    latest.setIsActive(true);
                    upsertAiFlag(latest, now, decision.note());
                }
                case SAFE -> {
                    latest.setAutoFlagged(false);
                    latest.setModerationStatus(ModerationStatus.APPROVED);
                    latest.setIsActive(true);
                }
            }
        } catch (Exception ex) {
            log.error("Toxicity analysis failed. reviewId={}, message={}", latest.getId(), ex.getMessage());
            latest.setModerationStatus(ModerationStatus.ANALYSIS_FAILED);
            latest.setAutoFlagged(false);
            latest.setIsActive(true);
            latest.setToxicityScore(null);
        }

        try {
            reviewRepository.save(latest);
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.warn("Optimistic locking conflict while saving toxicity result. reviewId={}", latest.getId());
            throw ex;
        }
    }

    private Decision decide(Map<String, Double> scores) {
        double insult = getScore(scores, "insult");
        double obscene = getScore(scores, "obscene");
        double toxic = max(getScore(scores, "toxic"), getScore(scores, "toxicity"));
        double maxScore = max(insult, obscene, toxic);

        double insultThreshold = toxicityProperties.getThresholds().getInsult();
        double obsceneThreshold = toxicityProperties.getThresholds().getObscene();
        double toxicThreshold = toxicityProperties.getThresholds().getToxic();

        boolean high = insult >= insultThreshold
                || obscene >= obsceneThreshold
                || toxic >= toxicThreshold;

        double borderlineFactor = 0.85d;
        boolean borderline = insult >= insultThreshold * borderlineFactor
                || obscene >= obsceneThreshold * borderlineFactor
                || toxic >= toxicThreshold * borderlineFactor;

        String note = String.format(Locale.ROOT,
                "scores={insult=%.4f, obscene=%.4f, toxic=%.4f} thresholds={insult=%.2f, obscene=%.2f, toxic=%.2f}",
                insult, obscene, toxic, insultThreshold, obsceneThreshold, toxicThreshold);

        if (high) {
            return new Decision(RiskLevel.HIGH, maxScore, note);
        }
        if (borderline) {
            return new Decision(RiskLevel.BORDERLINE, maxScore, note);
        }
        return new Decision(RiskLevel.SAFE, maxScore, note);
    }

    private void upsertAiFlag(Review review, LocalDateTime now, String note) {
        ReviewFlag flag = reviewFlagRepository.findOpenAiFlagByReviewId(review.getId()).orElseGet(() -> {
            ReviewFlag newFlag = new ReviewFlag();
            newFlag.setReview(review);
            newFlag.setReportedBy(null);
            newFlag.setReason(FlagReason.TOXIC_LANGUAGE);
            newFlag.setCreatedAt(now);
            newFlag.setIsActive(true);
            return newFlag;
        });
        flag.setNotes("Auto-flagged by toxicity engine. " + note);
        reviewFlagRepository.save(flag);
    }

    private double getScore(Map<String, Double> scores, String label) {
        if (scores == null) {
            return 0d;
        }
        return scores.getOrDefault(label.toLowerCase(Locale.ROOT), 0d);
    }

    private double max(double a, double b, double c) {
        return Math.max(a, Math.max(b, c));
    }

    private double max(double a, double b) {
        return Math.max(a, b);
    }

    @Transactional
    public void reanalyzeReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));
        analyzeAndApply(review);
    }

    private record Decision(RiskLevel riskLevel, Double maxScore, String note) {
    }

    private enum RiskLevel {
        HIGH,
        BORDERLINE,
        SAFE
    }
}

