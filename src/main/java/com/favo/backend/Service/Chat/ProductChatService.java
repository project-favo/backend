package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.AiChatMessage;
import com.favo.backend.Domain.chat.AiChatRole;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.chat.OpenAiChatTurn;
import com.favo.backend.Domain.chat.Repository.AiChatMessageRepository;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
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
    private final AiChatMessageRepository aiChatMessageRepository;
    private final OpenAIChatService openAIChatService;
    private final ProductAiContextService productAiContextService;

    @Transactional
    public ChatResponse chat(SystemUser principal, long productId, String userMessage) {
        SystemUser user = userService.getCurrentUserWithRelations(principal);

        Product product = productRepository.findByIdAndIsActiveTrueWithTag(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        List<OpenAiChatTurn> priorTurns = loadProductThreadChronological(user.getId(), productId);
        String productBlock = productAiContextService.buildContextBlock(
                product, productId, MAX_PRODUCT_DESCRIPTION_CHARS, MAX_REVIEW_SNIPPETS, MAX_REVIEW_SNIPPET_CHARS);

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
}
