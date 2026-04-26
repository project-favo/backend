package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

/**
 * İki ürünü yan yana karşılaştırmak için tek seferlik AI özeti. Sohbet geçmişi tutulmaz.
 */
@Service
@RequiredArgsConstructor
public class ProductCompareService {

    private static final int COMPARE_MAX_DESC = 2000;
    /**
     * Ürün başına alıntı: tek ürün sohbeti ({@link ProductChatService}) ile aynı (8×220) — iki ürün olduğu için
     * toplam yorum alıntısı 16; prompt ve maliyet sınırlı kalsın diye açıklama compare'de 2000 karakterle kısıtlı.
     */
    private static final int COMPARE_MAX_REVIEW_SNIPPETS = 8;
    private static final int COMPARE_MAX_REVIEW_SNIPPET_CHARS = 220;

    private static final String COMPARE_USER_MESSAGE =
            "Please compare these two products for someone choosing between them. "
                    + "Use the Favo context above and your general product knowledge. Follow all system rules.";

    private final ProductRepository productRepository;
    private final ProductAiContextService productAiContextService;
    private final OpenAIChatService openAIChatService;

    public ChatResponse compare(long productId1, long productId2) {
        if (productId1 == productId2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId1 and productId2 must differ");
        }

        Product p1 = productRepository.findByIdAndIsActiveTrueWithTag(productId1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        Product p2 = productRepository.findByIdAndIsActiveTrueWithTag(productId2)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        String block1 = productAiContextService.buildContextBlock(
                p1, productId1, COMPARE_MAX_DESC, COMPARE_MAX_REVIEW_SNIPPETS, COMPARE_MAX_REVIEW_SNIPPET_CHARS);
        String block2 = productAiContextService.buildContextBlock(
                p2, productId2, COMPARE_MAX_DESC, COMPARE_MAX_REVIEW_SNIPPETS, COMPARE_MAX_REVIEW_SNIPPET_CHARS);

        String fullSystem = OpenAIChatService.COMPARE_SYSTEM_PROMPT
                + "--- Product A (Favo) ---\n"
                + block1
                + "\n--- Product B (Favo) ---\n"
                + block2;

        return openAIChatService.completeConversation(fullSystem, Collections.emptyList(), COMPARE_USER_MESSAGE);
    }
}
