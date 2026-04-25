package com.favo.backend.Service.Moderation;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bilinen Türkçe ve İngilizce ağır küfür/hakaret ifadelerini yakalar.
 * Model bazlı analiz bu kısa/kaba kelimeleri ıskalasa bile bu filtre devreye girer.
 * Kelimeler küçük harfe normalize edildikten sonra tam kelime sınırıyla (word-boundary) eşleştirilir.
 */
public final class ProfanityFilter {

    private ProfanityFilter() {}

    /** Tek kelime veya kök olarak geçmesi yeterli olan terimler (word-boundary ile). */
    private static final Set<String> BLOCKED_WORDS = Set.of(
            // ── Türkçe ──────────────────────────────────────────────────────────
            "sik", "sikerim", "sikeyim", "sikiyim", "sikiyor", "sikik", "sikiş", "sikişmek",
            "orospu", "orospuçocuğu", "fahişe",
            "amk", "amına", "amcık", "amını",
            "göt", "götü", "göte",
            "yarrak", "yarak",
            "pezevenk", "piç", "piçlik",
            "ibne", "ibnelik",
            "oç", "oğlum",   // "oç" hakaret bağlamı
            "bok", "boktan",
            "meme", "memeler",   // hakaret bağlamı
            "kahpe", "kahpelik",
            "salak", "salağı",   // ılımlı ama yaygın hakaret
            "aptal",             // ılımlı ama hakaret
            "gerizekalı", "gerizekalının",
            "beyinsiz",
            "haysiyetsiz",
            "aşağılık",
            "şerefsiz",
            "alçak",
            "puşt",
            "götveren",
            "kötü",              // bağlamdan bağımsız olarak zayıf; gerekirse kaldırın
            // ── İngilizce ───────────────────────────────────────────────────────
            "fuck", "fucker", "fucking", "fucked", "fucks",
            "shit", "shitty",
            "asshole", "ass",
            "bitch", "bitches",
            "cunt",
            "dick", "dicks",
            "cock", "cocks",
            "pussy",
            "bastard",
            "motherfucker", "motherfuckers",
            "nigger", "nigga",
            "faggot", "fag",
            "whore",
            "slut",
            "retard", "retarded",
            "idiot",
            "moron",
            "dumbass",
            "jackass"
    );

    /** Kısmen geçtiğinde de yeterli olan alt dizeler (substring). */
    private static final Set<String> BLOCKED_SUBSTRINGS = Set.of(
            "sikeyim", "sikiyor", "orospu", "amına", "yarrak", "pezevenk",
            "motherfuck", "bullshit", "horseshit", "wtf"
    );

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    /**
     * Metin, bilinen en az bir yasak kelime/alt dize içeriyorsa {@code true} döner.
     */
    public static boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) return false;

        String normalized = text.toLowerCase()
                .replace("ı", "i")
                .replace("ş", "s")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace("ö", "o")
                .replace("ç", "c")
                .replace("İ", "i")
                .replace("Ş", "s")
                .replace("Ğ", "g")
                .replace("Ü", "u")
                .replace("Ö", "o")
                .replace("Ç", "c");

        // Kelime bazlı eşleme
        String[] tokens = WORD_SPLIT.split(normalized);
        for (String token : tokens) {
            if (BLOCKED_WORDS.contains(token)) return true;
        }

        // Alt dize eşlemesi (bitişik yazılmış, örn. "motherfuckers")
        for (String sub : BLOCKED_SUBSTRINGS) {
            if (normalized.contains(sub)) return true;
        }

        return false;
    }
}
