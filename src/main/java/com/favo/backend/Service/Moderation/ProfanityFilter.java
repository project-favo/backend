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

    /** Tek kelime veya kök olarak geçmesi yeterli olan terimler — normalize edilmiş (ASCII) formda. */
    private static final Set<String> BLOCKED_WORDS = Set.of(
            // ── Türkçe (normalize: ş→s, ğ→g, ü→u, ö→o, ç→c, ı→i) ───────────────
            "sik", "sikerim", "sikeyim", "sikiyim", "sikiyor", "sikik", "sikis", "sikismek",
            "orospu", "orospucocugu", "fahise",
            "amk", "amina", "amcik", "amini",
            "got", "gotu", "gote", "gotveren",
            "yarrak", "yarak",
            "pezevenk", "pic", "piclik",
            "ibne", "ibnelik",
            "oc",
            "bok", "boktan",
            "kahpe", "kahpelik",
            "salak", "salagi",
            "aptal",
            "gerizekalinin",
            "beyinsiz",
            "haysiyetsiz",
            "asagilik",
            "serefsiz",
            "alcak",
            "pust",
            "kotu",
            // ── İngilizce ───────────────────────────────────────────────────────
            "fuck", "fucker", "fuckers", "fucking", "fucked", "fucks",
            "shit", "shitty",
            "asshole", "assholes", "ass",
            "bitch", "bitches",
            "cunt", "cunts",
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

    /** Kısmen geçtiğinde de yeterli olan alt dizeler (substring) — normalize edilmiş formda. */
    private static final Set<String> BLOCKED_SUBSTRINGS = Set.of(
            "sikeyim", "sikiyor", "orospu", "amina", "yarrak", "pezevenk",
            "motherfuck", "bullshit", "horseshit", "fuckin", "asshole"
    );

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    /**
     * Metin, bilinen en az bir yasak kelime/alt dize içeriyorsa {@code true} döner.
     * "4" karakteri hem "a" (standart leet) hem "u" ("f4ck"→"fuck") olarak iki geçişte denenir.
     */
    public static boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) return false;

        // Geçiş 1 — 4→a (standart leet: "h4te" → "hate")
        if (checkNormalized(normalize(text, '4', 'a'))) return true;

        // Geçiş 2 — 4→u ("f4ck" → "fuck")
        if (checkNormalized(normalize(text, '4', 'u'))) return true;

        return false;
    }

    private static String normalize(String text, char leetChar, char replacement) {
        return text.toLowerCase()
                // Türkçe karakterler
                .replace("ı", "i").replace("İ", "i")
                .replace("ş", "s").replace("Ş", "s")
                .replace("ğ", "g").replace("Ğ", "g")
                .replace("ü", "u").replace("Ü", "u")
                .replace("ö", "o").replace("Ö", "o")
                .replace("ç", "c").replace("Ç", "c")
                // Leet speak: sayı/sembol → harf
                .replace("0", "o")
                .replace("1", "i")
                .replace("3", "e")
                .replace(String.valueOf(leetChar), String.valueOf(replacement))
                .replace("5", "s")
                .replace("7", "t")
                .replace("@", "a")
                .replace("$", "s")
                .replace("|", "i")
                // Tekrarlanan karakterleri birleştir (fuuuck → fuck)
                .replaceAll("(.)\\1{2,}", "$1$1");
    }

    private static boolean checkNormalized(String normalized) {
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
