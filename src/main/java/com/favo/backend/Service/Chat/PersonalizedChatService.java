package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.AiChatMessage;
import com.favo.backend.Domain.chat.AiChatRole;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.chat.OpenAiChatTurn;
import com.favo.backend.Domain.chat.Repository.AiChatMessageRepository;
import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.Repository.UserFollowRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserFollow;
import com.favo.backend.Service.User.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalizedChatService {

    private static final int MAX_LIKED_PRODUCTS = 15;
    private static final int MAX_FOLLOWS = 15;
    private static final int MAX_RECENT_REVIEWS = 8;
    private static final int MAX_HISTORY_MESSAGES = 40;

    private final UserService userService;
    private final ProductInteractionRepository productInteractionRepository;
    private final UserFollowRepository userFollowRepository;
    private final ReviewRepository reviewRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final OpenAIChatService openAIChatService;

    @Transactional
    public ChatResponse chat(SystemUser principal, String userMessage) {
        SystemUser user = userService.getCurrentUserWithRelations(principal);

        List<OpenAiChatTurn> priorTurns = loadPriorTurnsChronological(user.getId());
        String personalizationBlock = buildPersonalizationBlock(user);

        String fullSystem = OpenAIChatService.BASE_SYSTEM_PROMPT
                + "\n\n--- Personalized context (do not quote verbatim; use to tailor help) ---\n"
                + personalizationBlock;

        ChatResponse response = openAIChatService.completeConversation(fullSystem, priorTurns, userMessage);

        persistMessage(user, AiChatRole.USER, userMessage);
        persistMessage(user, AiChatRole.ASSISTANT, response.getReply());

        return response;
    }

    private void persistMessage(SystemUser user, AiChatRole role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setOwner(user);
        m.setRole(role);
        m.setContent(content);
        aiChatMessageRepository.save(m);
    }

    private List<OpenAiChatTurn> loadPriorTurnsChronological(Long ownerId) {
        List<AiChatMessage> recentDesc = aiChatMessageRepository.findByOwnerIdAndIsActiveTrueOrderByCreatedAtDesc(
                ownerId,
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

    private String buildPersonalizationBlock(SystemUser user) {
        StringBuilder sb = new StringBuilder();

        sb.append("Account: username=").append(nullToEmpty(user.getUserName()))
                .append(", display name=").append(trimName(user.getName(), user.getSurname()))
                .append(", email=").append(user.getEmail());
        if (user.getUserType() != null) {
            sb.append(", user type=").append(user.getUserType().getName());
        }
        sb.append(".\n");

        if (!(user instanceof GeneralUser gu)) {
            sb.append("Social/product activity: not available for this account type.\n");
            return sb.toString();
        }

        var likedPage = productInteractionRepository.findLikedProductsByPerformerId(
                gu.getId(),
                PageRequest.of(0, MAX_LIKED_PRODUCTS)
        );
        if (likedPage.isEmpty()) {
            sb.append("Liked products (wishlist): none on file.\n");
        } else {
            sb.append("Liked products (recent): ");
            List<String> parts = new ArrayList<>();
            for (ProductInteraction pi : likedPage.getContent()) {
                Product p = pi.getTargetProduct();
                if (p != null && Boolean.TRUE.equals(p.getIsActive())) {
                    parts.add("\"" + p.getName() + "\" (id=" + p.getId() + ")");
                }
            }
            sb.append(parts.isEmpty() ? "none" : String.join("; ", parts)).append(".\n");
        }

        List<UserFollow> follows = userFollowRepository.findByFollowerIdAndIsActiveTrueOrderByCreatedAtDesc(
                gu.getId(),
                PageRequest.of(0, MAX_FOLLOWS)
        );
        if (follows.isEmpty()) {
            sb.append("Followed users: none on file.\n");
        } else {
            sb.append("Followed users: ");
            List<String> names = new ArrayList<>();
            for (UserFollow uf : follows) {
                GeneralUser f = uf.getFollowee();
                if (f != null && Boolean.TRUE.equals(f.getIsActive())) {
                    names.add("@" + f.getUserName());
                }
            }
            sb.append(names.isEmpty() ? "none" : String.join(", ", names)).append(".\n");
        }

        List<Review> reviews = reviewRepository.findByOwnerIdAndIsActiveTrueOrderByCreatedAtDesc(
                gu.getId(),
                PageRequest.of(0, MAX_RECENT_REVIEWS)
        );
        if (reviews.isEmpty()) {
            sb.append("User's recent reviews: none on file.\n");
        } else {
            sb.append("User's recent reviews:\n");
            for (Review r : reviews) {
                String productName = r.getProduct() != null ? r.getProduct().getName() : "?";
                String snippet = shorten(nullToEmpty(r.getDescription()), 220);
                sb.append("- ")
                        .append(nullToEmpty(r.getTitle()))
                        .append(" | product: ")
                        .append(productName)
                        .append(" | rating: ")
                        .append(r.getRating())
                        .append("/5")
                        .append(snippet.isEmpty() ? "" : " | note: " + snippet)
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private static String trimName(String name, String surname) {
        String n = name == null ? "" : name.trim();
        String s = surname == null ? "" : surname.trim();
        if (n.isEmpty() && s.isEmpty()) {
            return "(not set)";
        }
        return (n + " " + s).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String shorten(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}
