package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Ürün + yorum özetlerini OpenAI prompt bloklarına dönüştürür (ürün sohbeti ve compare için).
 */
@Service
@RequiredArgsConstructor
public class ProductAiContextService {

    private final ReviewRepository reviewRepository;

    public String buildContextBlock(
            Product product,
            long productId,
            int maxDescriptionChars,
            int maxReviewSnippets,
            int maxReviewSnippetChars
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("product_id=").append(productId).append("\n");
        sb.append("name: ").append(product.getName() == null ? "" : product.getName()).append("\n");

        String categoryLine = categoryPathLine(product);
        if (!categoryLine.isEmpty()) {
            sb.append("category: ").append(categoryLine).append("\n");
        }

        String desc = product.getDescription() == null ? "" : product.getDescription().trim();
        if (!desc.isEmpty()) {
            if (desc.length() > maxDescriptionChars) {
                desc = desc.substring(0, maxDescriptionChars) + "…";
            }
            sb.append("description:\n").append(desc).append("\n");
        }

        Double avg = reviewRepository.calculateAverageRatingByProductId(productId);
        Long count = reviewRepository.countReviewsByProductId(productId);
        if (count != null && count > 0) {
            String avgStr = avg != null ? String.format(Locale.US, "%.2f", avg) : "?";
            sb.append("community_reviews: average_rating=").append(avgStr).append("/5, count=").append(count).append("\n");
        } else {
            sb.append("community_reviews: none yet.\n");
        }

        List<Review> recent = reviewRepository.findByProduct_IdAndIsActiveTrueOrderByCreatedAtDesc(
                productId,
                PageRequest.of(0, maxReviewSnippets)
        );
        if (!recent.isEmpty()) {
            sb.append("recent_review_excerpts (opinions, not verified facts):\n");
            for (Review r : recent) {
                String title = r.getTitle() == null ? "" : r.getTitle().trim();
                String body = r.getDescription() == null ? "" : r.getDescription().trim().replaceAll("\\s+", " ");
                if (body.length() > maxReviewSnippetChars) {
                    body = body.substring(0, maxReviewSnippetChars) + "…";
                }
                sb.append("- ").append(r.getRating()).append("/5");
                if (!title.isEmpty()) {
                    sb.append(" | ").append(title);
                }
                if (!body.isEmpty()) {
                    sb.append(" | ").append(body);
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static String categoryPathLine(Product product) {
        if (product.getTag() == null) {
            return "";
        }
        String tagName = product.getTag().getName() != null ? product.getTag().getName() : "";
        if (product.getTag().getParent() != null && product.getTag().getParent().getName() != null) {
            String parent = product.getTag().getParent().getName();
            if (!parent.isEmpty() && !parent.equalsIgnoreCase(tagName)) {
                return parent + " → " + tagName;
            }
        }
        return tagName;
    }
}
