package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.AiChatMessage;
import com.favo.backend.Domain.chat.AiChatRole;
import com.favo.backend.Domain.chat.ChatProductCardDto;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.chat.OpenAiChatTurn;
import com.favo.backend.Domain.chat.Repository.AiChatMessageRepository;
import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.favo.backend.Domain.product.Tag;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonalizedChatService {

    private static final int MAX_LIKED_PRODUCTS = 15;
    private static final int MAX_FOLLOWS = 15;
    private static final int MAX_RECENT_REVIEWS = 8;
    private static final int MAX_HISTORY_MESSAGES = 40;
    private static final int MAX_CATEGORY_CATALOG_CHARS = 4500;
    /** Son birkaç tur; ürün carousel arama metni buradan beslenir (kısa “evet” / “yes” sonrası konu sapmasını azaltır). */
    private static final int MAX_PRIOR_TURNS_FOR_FEED_CONTEXT = 12;
    private static final int MAX_FEED_RETRIEVAL_CHARS = 4000;

    private final UserService userService;
    private final ProductInteractionRepository productInteractionRepository;
    private final UserFollowRepository userFollowRepository;
    private final ReviewRepository reviewRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final OpenAIChatService openAIChatService;
    private final ChatProductFeedService chatProductFeedService;
    private final TagRepository tagRepository;

    private static final String CAROUSEL_SYSTEM_HINT =
            "\n\nA product carousel with real items from our database will appear below your message in the app. "
                    + "Keep the reply short (1–3 sentences). Align your answer with those cards (same topic/category); "
                    + "do not describe a different product category than the carousel. "
                    + "Do not invent product names that are not in the context above.";

    @Transactional
    public ChatResponse chat(SystemUser principal, String userMessage) {
        SystemUser user = userService.getCurrentUserWithRelations(principal);

        List<OpenAiChatTurn> priorTurns = loadPriorTurnsChronological(user.getId());
        String personalizationBlock = buildPersonalizationBlock(user);

        String feedRetrievalText = buildFeedRetrievalContext(priorTurns, userMessage);
        List<ChatProductCardDto> productFeed = chatProductFeedService.buildFeed(user, userMessage, feedRetrievalText);

        String fullSystem = OpenAIChatService.BASE_SYSTEM_PROMPT
                + "\n\n--- Personalized context (background; prioritize this chat thread over unrelated wishlist items) ---\n"
                + personalizationBlock
                + (productFeed.isEmpty() ? "" : CAROUSEL_SYSTEM_HINT);

        ChatResponse response = openAIChatService.completeConversation(fullSystem, priorTurns, userMessage);
        response.setProducts(productFeed);

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
        List<AiChatMessage> recentDesc = aiChatMessageRepository.findByOwnerIdAndProductIsNullAndIsActiveTrueOrderByCreatedAtDesc(
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

    /**
     * Carousel araması için son turları + güncel mesajı birleştirir; yalnızca son mesaj “evet” gibi kısa olduğunda
     * önceki kullanıcı niyeti (kitap, telefon vb.) sorguya yansır.
     */
    private static String buildFeedRetrievalContext(List<OpenAiChatTurn> priorTurnsOldestFirst, String userMessage) {
        if (priorTurnsOldestFirst == null || priorTurnsOldestFirst.isEmpty()) {
            return userMessage;
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, priorTurnsOldestFirst.size() - MAX_PRIOR_TURNS_FOR_FEED_CONTEXT);
        for (int i = start; i < priorTurnsOldestFirst.size(); i++) {
            OpenAiChatTurn t = priorTurnsOldestFirst.get(i);
            sb.append(t.role()).append(": ").append(t.content()).append('\n');
        }
        sb.append("user: ").append(userMessage == null ? "" : userMessage);
        String full = sb.toString();
        if (full.length() <= MAX_FEED_RETRIEVAL_CHARS) {
            return full;
        }
        return full.substring(full.length() - MAX_FEED_RETRIEVAL_CHARS);
    }

    private String buildPersonalizationBlock(SystemUser user) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildFavoCategoryCatalogForPrompt());
        sb.append("\n");

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

        List<UserFollow> follows = userFollowRepository.findByFollower_IdAndIsActiveTrueOrderByCreatedAtDesc(
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

    /**
     * Aktif tag ağacı — alt kategoriler (Smartphones, Smartwatches vb.) modele açıkça verilir;
     * aksi halde model uydurarak "kategori yok" diyebiliyordu.
     */
    private String buildFavoCategoryCatalogForPrompt() {
        List<Tag> all = tagRepository.findAllActiveWithParentOrderByCategoryPath();
        Map<Long, List<Tag>> byParentId = new HashMap<>();
        List<Tag> roots = new ArrayList<>();
        for (Tag t : all) {
            if (t.getParent() == null) {
                roots.add(t);
            } else {
                byParentId.computeIfAbsent(t.getParent().getId(), k -> new ArrayList<>()).add(t);
            }
        }
        for (List<Tag> kids : byParentId.values()) {
            kids.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));
        }
        roots.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));

        StringBuilder sb = new StringBuilder();
        sb.append("Favo category tree (database; includes all subcategories). ")
                .append("In the app: Search → top-level name → then subcategories listed under each. ")
                .append("Do not say a category or subcategory is missing if it appears below. ")
                .append("Chat product cards are real DB matches.\n");

        for (Tag root : roots) {
            sb.append("- ").append(root.getName()).append(": ");
            List<String> under = new ArrayList<>();
            collectDescendantTagNames(root.getId(), byParentId, under);
            under.sort(String.CASE_INSENSITIVE_ORDER);
            sb.append(under.isEmpty() ? "(no sub-tags)" : String.join(", ", under));
            sb.append("\n");
        }

        String out = sb.toString();
        if (out.length() > MAX_CATEGORY_CATALOG_CHARS) {
            return out.substring(0, MAX_CATEGORY_CATALOG_CHARS - 3) + "...\n";
        }
        return out;
    }

    private static void collectDescendantTagNames(Long parentId, Map<Long, List<Tag>> byParentId, List<String> out) {
        List<Tag> children = byParentId.get(parentId);
        if (children == null) {
            return;
        }
        for (Tag c : children) {
            out.add(c.getName());
            collectDescendantTagNames(c.getId(), byParentId, out);
        }
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
