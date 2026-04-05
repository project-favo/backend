package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.AiChatMessage;
import com.favo.backend.Domain.chat.AiChatRole;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.chat.OpenAiChatTurn;
import com.favo.backend.Domain.chat.Repository.AiChatMessageRepository;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.User.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ürün detay ekranından açılan, tek ürüne bağlı sohbet. Genel asistan geçmişinden ayrı tutulur ({@code product_id}).
 */
@Service
@RequiredArgsConstructor
public class ProductChatService {

    private static final int MAX_HISTORY_MESSAGES = 40;
    private static final int MAX_REVIEW_SNIPPETS = 8;
    private static final int MAX_REVIEW_SNIPPET_CHARS = 220;
    private static final int MAX_PRODUCT_DESCRIPTION_CHARS = 3500;

    private final UserService userService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final OpenAIChatService openAIChatService;

    @Transactional
    public ChatResponse chat(SystemUser principal, long productId, String userMessage) {
        SystemUser user = userService.getCurrentUserWithRelations(principal);

        Product product = productRepository.findByIdAndIsActiveTrueWithTag(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        List<OpenAiChatTurn> priorTurns = loadProductThreadChronological(user.getId(), productId);
        String productBlock = buildProductContextBlock(product, productId);

        String fullSystem = OpenAIChatService.PRODUCT_CHAT_SYSTEM_PREFIX
                + "--- Product context (facts + community reviews) ---\n"
                + productBlock;

        ChatResponse response = openAIChatService.completeConversation(fullSystem, priorTurns, userMessage);

        persistMessage(user, product, AiChatRole.USER, userMessage);
        persistMessage(user, product, AiChatRole.ASSISTANT, response.getReply());

        return response;
    }

    private void persistMessage(SystemUser user, Product product, AiChatRole role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setOwner(user);
        m.setProduct(product);
        m.setRole(role);
        m.setContent(content);
        aiChatMessageRepository.save(m);
    }

    private List<OpenAiChatTurn> loadProductThreadChronological(Long ownerId, Long productId) {
        List<AiChatMessage> recentDesc = aiChatMessageRepository.findByOwnerIdAndProduct_IdAndIsActiveTrueOrderByCreatedAtDesc(
                ownerId,
                productId,
                PageRequest.of(0, MAX_HISTORY_MESSAGES)
        );
        List<AiChatMessage> oldestFirst = new ArrayList<>(recentDesc);
        Collections.reverse(oldestFirst);

        List<OpenAiChatTurn> turns = new ArrayList<>();
        for (AiChatMessage m : oldestFirst) {
            String role = m.getRole() == AiChatRole.USER ? "user" : "assistant";
            turns.add(new OpenAiChatTurn(role, m.getContent()));
        }
        return turns;
    }

    private String buildProductContextBlock(Product product, long productId) {
        StringBuilder sb = new StringBuilder();
        sb.append("product_id=").append(productId).append("\n");
        sb.append("name: ").append(product.getName() == null ? "" : product.getName()).append("\n");

        String categoryLine = categoryPathLine(product);
        if (!categoryLine.isEmpty()) {
            sb.append("category: ").append(categoryLine).append("\n");
        }

        String desc = product.getDescription() == null ? "" : product.getDescription().trim();
        if (!desc.isEmpty()) {
            if (desc.length() > MAX_PRODUCT_DESCRIPTION_CHARS) {
                desc = desc.substring(0, MAX_PRODUCT_DESCRIPTION_CHARS) + "…";
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
                PageRequest.of(0, MAX_REVIEW_SNIPPETS)
        );
        if (!recent.isEmpty()) {
            sb.append("recent_review_excerpts (opinions, not verified facts):\n");
            for (Review r : recent) {
                String title = r.getTitle() == null ? "" : r.getTitle().trim();
                String body = r.getDescription() == null ? "" : r.getDescription().trim().replaceAll("\\s+", " ");
                if (body.length() > MAX_REVIEW_SNIPPET_CHARS) {
                    body = body.substring(0, MAX_REVIEW_SNIPPET_CHARS) + "…";
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
