package com.favo.backend.Domain.chat;

/**
 * Structured result from the AI that includes both the conversational reply and
 * product search intent extracted from the user's message.
 *
 * @param reply             The conversational text reply to show the user.
 * @param productSearchQuery 1-3 word search term for product retrieval, or null if no carousel is needed.
 * @param preferHighRated   True if the user explicitly wants highly rated / best reviewed products.
 * @param useUserLikes      True if the user explicitly asked for recommendations based on their likes.
 */
public record OpenAiIntentResult(
        String reply,
        String productSearchQuery,
        boolean preferHighRated,
        boolean useUserLikes
) {}
