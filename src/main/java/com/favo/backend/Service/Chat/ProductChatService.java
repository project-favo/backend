package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductChatService {

    private static final int MAX_REVIEWS_IN_PROMPT = 10;
    private static final int MAX_REVIEW_SNIPPET = 220;

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final OpenAIChatService openAIChatService;

    public ChatResponse chat(Long productId, String userMessage) {
        Product product = productRepository.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        List<Review> reviews = reviewRepository.findByProductIdWithRelations(productId);

        String systemPrompt = buildSystemPrompt(product, reviews);
        return openAIChatService.completeConversation(systemPrompt, List.of(), userMessage);
    }

    private String buildSystemPrompt(Product product, List<Review> reviews) {
        String name = product.getName() != null ? product.getName() : "Unknown Product";
        String category = product.getTag() != null ? product.getTag().getCategoryPath() : "";
        String description = product.getDescription() != null && !product.getDescription().isBlank()
                ? product.getDescription()
                : "No description available.";

        long reviewCount = reviews.size();
        double avgRating = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        String avgStr = reviewCount > 0 ? String.format("%.1f", avgRating) : "No ratings yet";

        StringBuilder reviewsBlock = new StringBuilder();
        int shown = 0;
        for (Review r : reviews) {
            if (shown >= MAX_REVIEWS_IN_PROMPT) break;
            reviewsBlock.append("- [")
                    .append(r.getRating()).append("/5] ")
                    .append(r.getTitle() != null ? r.getTitle() : "(no title)")
                    .append(": ")
                    .append(shorten(r.getDescription(), MAX_REVIEW_SNIPPET))
                    .append("\n");
            shown++;
        }
        if (reviewsBlock.isEmpty()) {
            reviewsBlock.append("No community reviews yet.\n");
        }

        return "You are the product assistant for \"" + name + "\" on Favo.\n"
                + "Favo is a mobile product-review app. There is no checkout and no pricing — do NOT invent prices.\n\n"
                + "=== Product details ===\n"
                + "Name: " + name + "\n"
                + "Category: " + category + "\n"
                + "Description: " + description + "\n\n"
                + "=== Community reviews ===\n"
                + "Average rating: " + avgStr + " / 5  (" + reviewCount + " review" + (reviewCount != 1 ? "s" : "") + ")\n"
                + reviewsBlock + "\n"
                + "=== Instructions ===\n"
                + "- Answer questions about this product using the details and reviews above.\n"
                + "- You may also use your general knowledge about the product to enrich answers (specs, common use-cases, comparisons), "
                + "but clearly distinguish what comes from your general knowledge vs. from the community reviews above.\n"
                + "- If you are unsure about a specific spec, say so and suggest checking the manufacturer's official website.\n"
                + "- Keep replies concise: 2–4 sentences unless the user asks for detail.\n"
                + "- Reply in the same language as the user's message.\n"
                + "- Do not suggest other shopping platforms or invent availability/stock info.\n";
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
