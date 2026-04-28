package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.ChatProductCardDto;
import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.favo.backend.Domain.product.Tag;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatProductFeedService {

    private static final int FEED_SIZE = 8;
    private static final int CANDIDATE_POOL = 40;
    private static final int MAX_LIKES_FOR_TAGS = 25;
    private static final int MAX_TAG_IDS = 50;
    private static final int MAX_PREFERRED_TAGS_FROM_LIKES = 3;
    private static final double MIN_DOMINANT_CATEGORY_SHARE = 0.55;
    private static final int MIN_RELEVANCE_FOR_STRICT_INTENT = 35;
    private static final int MIN_RELEVANCE_FOR_GENERIC_QUERY = 25;
    /** Q birden çok sözcük olduğunda (ve zorunlu terimler varsa) eşik yükser; çapraz kategori sızmasını azaltır. */
    private static final int MIN_RELEVANCE_FOR_MULTIWORD = 32;
    private static final int MIN_RESULTS_AFTER_GUARDRAILS = 2;

    /**
     * Ağızdan/ niyetten gelen jenerik sorgu; isimde kelimenin aynen geçmemesi normal (kategori+ürün ailesi eşleşir).
     * {@link #productMeetsQueryTextSpecificity} tek-sözcük "zorunlu metin" kuralını bunlar için uygulamaz.
     */
    private static final Set<String> GENERIC_INTENT_QUERIES = Set.of(
            "smartphone", "smartwatch", "book", "books", "laptop", "tablet", "telefon", "headphones",
            "headphone", "phone", "tv", "tvs"
    );

    /**
     * Sadece çok kelimeli / karışık ifadeler (tek kelime kategori adları token aramasıyla zaten yakalanır).
     */
    private static final LinkedHashMap<String, String> PHRASE_TO_SEARCH_Q = new LinkedHashMap<>();

    static {
        PHRASE_TO_SEARCH_Q.put("dizüstü bilgisayar", "laptop");
        PHRASE_TO_SEARCH_Q.put("cep telefonu", "telefon");
        PHRASE_TO_SEARCH_Q.put("akıllı telefon", "telefon");
        PHRASE_TO_SEARCH_Q.put("akıllı saat", "smartwatch");
        PHRASE_TO_SEARCH_Q.put("akilli saat", "smartwatch");
        PHRASE_TO_SEARCH_Q.put("smart phone", "smartphone");
        PHRASE_TO_SEARCH_Q.put("t-shirt", "shirt");
        PHRASE_TO_SEARCH_Q.put("gaming laptop", "gaming laptop");
    }

    private static final Pattern WORD_BEFORE_KATEGORI =
            Pattern.compile("([\\p{L}\\p{N}]{2,})\\s+kategorisindeki", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern KATEGORI_PREFIX =
            Pattern.compile("kategor(?:i|ide)\\s*(?:[:\\-]|\\s)\\s*([\\p{L}\\p{N}]{2,})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "kitaplardan öner" — tüm sözcük yakalanır, kök stripCommonTurkishSuffix ile çıkarılır */
    private static final Pattern ABLATIVE_WORD_THEN_VERB = Pattern.compile(
            "([\\p{L}\\p{N}]{3,}(?:larından|lerinden|lardan|lerden|dan|den|tan|ten))\\s+(?:öner|oner|getir|göster|goster|bul|listele)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "öner bana kitap", "getir laptop" */
    private static final Pattern VERB_THEN_NOUN = Pattern.compile(
            "(?:^|\\s)(?:öner|oner|getir|göster|goster|bul|listele)(?:\\s+bana|\\s+bize)?\\s+([\\p{L}\\p{N}]{2,})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "bana kitap öner" */
    private static final Pattern BANA_NOUN_VERB = Pattern.compile(
            "bana\\s+([\\p{L}\\p{N}]{2,})\\s+(?:öner|oner|getir|göster|goster)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** English: category: books, category = electronics */
    private static final Pattern EN_CATEGORY_PREFIX = Pattern.compile(
            "category\\s*[:=\\-]\\s*([\\p{L}\\p{N}]{2,})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** in the laptop category / from the shoe category */
    private static final Pattern EN_IN_THE_CATEGORY = Pattern.compile(
            "(?:in|from)\\s+the\\s+([\\p{L}\\p{N}]{2,})\\s+category",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /**
     * recommend / suggest + çok sözcük (en fazla 4 sözcük); "suggest me men pants" → men pants, "suggest me an iphone" → an iphone (normalize’da a/an/the atılır).
     */
    private static final Pattern EN_RECOMMEND_SUGGEST = Pattern.compile(
            "(?:recommend|suggest)(?:\\s+me|\\s+us)?\\s+((?:[\\p{L}\\p{N}]+)(?:\\s+[\\p{L}\\p{N}]+){0,3})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** show (me) / (us) + birkaç sözcük; "show shoes" (me yok) da geçerli */
    private static final Pattern EN_SHOW_ME = Pattern.compile(
            "show\\s+(?:(?:me|us)\\s+)?((?:[\\p{L}\\p{N}]+)(?:\\s+[\\p{L}\\p{N}]+){0,3})(?:\\s+products?)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "book products" — en az 3 harf; "rated products" gibi gürültü stopword ile elenir */
    private static final Pattern EN_NOUN_PRODUCTS = Pattern.compile(
            "\\b([\\p{L}\\p{N}]{3,})\\s+products?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** find / search for + birkaç sözcük */
    private static final Pattern EN_FIND_SEARCH = Pattern.compile(
            "(?:find|search|browse|list)\\s+(?:for\\s+)?((?:[\\p{L}\\p{N}]+)(?:\\s+[\\p{L}\\p{N}]+){0,3})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "facebook" / "notebook" içinde yanlış eşleşmeyi engellemek için */
    private static final Pattern EN_BOOK_WORD = Pattern.compile("\\bbooks?\\b", Pattern.CASE_INSENSITIVE);

    private static final Locale LOCALE_TR = Locale.forLanguageTag("tr");

    private static final Set<String> STOPWORDS_FOR_CATEGORY = Set.of(
            "bana", "beni", "bunu", "şu", "bu", "the", "for", "getir", "göster", "goster",
            "ürün", "urun", "ürünleri", "urunleri", "öner", "oner", "listele", "tüm", "tum", "some"
    );

    /** Token çıkarımında yok sayılacak kelimeler (Türkçe + yaygın İngilizce) */
    private static final Set<String> STOPWORDS_TOKENS = buildStopwordTokens();

    private static Set<String> buildStopwordTokens() {
        Set<String> s = new HashSet<>(STOPWORDS_FOR_CATEGORY);
        String[] more = {
                "ve", "veya", "ile", "için", "icin", "gibi", "bir", "o", "da", "de", "ki",
                "mi", "mı", "mu", "mü", "çok", "cok", "var", "yok", "ne", "nasıl", "nasil",
                "neden", "hangi", "her", "hiç", "hic", "daha", "en", "şey", "sey", "ben", "sen",
                "sana", "bize", "size", "bunun", "şunu", "sunu", "lütfen", "lutfen",
                "favo", "destek", "yardım", "yardim", "merhaba", "selam", "thanks", "thank", "please",
                "show", "bring", "give", "want", "need", "some", "any", "help", "me", "you", "us", "my",
                "satın", "satin", "almak", "al", "sat", "fiyat", "ucuz", "pahalı", "pahali",
                "yüksek", "yuksek", "puanlı", "puanli", "iyi", "kötü", "kotu", "yeni", "eski",
                "rated", "rating", "highly", "products", "product", "items", "item", "lists", "list",
                "star", "stars", "review", "reviews", "average", "score", "scores", "sort", "sorted",
                "best", "top", "good", "great", "nice", "well", "also", "just", "only", "very",
                "the", "and", "for", "with", "from", "that", "this", "what", "which", "your", "our",
                "can", "could", "would", "should", "how", "when", "where", "why", "are", "was", "were",
                "have", "has", "had", "get", "got", "let", "make", "made", "see", "seen", "look", "looking"
        };
        for (String w : more) {
            s.add(w.toLowerCase(LOCALE_TR));
        }
        return s;
    }

    private static final Set<String> LEADING_ARTICLE_WORDS = Set.of("a", "an", "the");
    private static final Set<String> TRAILING_NOISE_WORDS = Set.of("please", "thanks", "thank", "plz", "thx");
    private static final Set<String> GREETING_ONLY_WORDS = Set.of(
            "hi", "hello", "hey", "selam", "merhaba", "slm", "sa", "yo"
    );

    /**
     * Tek başına arama sorgusu olarak çok geniş (ör. "suggest me men" → sadece "men" bir sonraki
     * denemelere bırakılsın diye ayrı ağırlanır; çok sözcüklü "men pants" buna girmez.
     */
    private static final Set<String> WEAK_STANDALONE_QUERIES = Set.of(
            "men", "women", "woman", "kids", "kid", "girls", "girl", "boys", "boy"
    );

    /**
     * Çok sözcüklü sorguda her terimin aynen üründe aranmaz; cinsiyet/segment sözcükleri (men + pants → yalnız "pants" zorunlu).
     */
    private static final Set<String> MULTIWORD_OPTIONAL_TOKENS = new HashSet<>();

    static {
        MULTIWORD_OPTIONAL_TOKENS.addAll(WEAK_STANDALONE_QUERIES);
        MULTIWORD_OPTIONAL_TOKENS.add("erkek");
        MULTIWORD_OPTIONAL_TOKENS.add("kadın");
        MULTIWORD_OPTIONAL_TOKENS.add("kadin");
        MULTIWORD_OPTIONAL_TOKENS.add("bayan");
        MULTIWORD_OPTIONAL_TOKENS.add("mens");
        MULTIWORD_OPTIONAL_TOKENS.add("womens");
    }

    /**
     * recommend/suggest/show/find ile yakalanan ifadeyi a/an/the + kırpık nokta ile sadeleştirir.
     */
    static String normalizeQueryPhrase(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = t.replaceAll("[.!?…]+$", "").trim();
        String[] parts = t.split("\\s+");
        if (parts.length == 0) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            list.add(p);
        }
        while (!list.isEmpty() && LEADING_ARTICLE_WORDS.contains(list.get(0).toLowerCase(Locale.ROOT))) {
            list.remove(0);
        }
        if (list.isEmpty()) {
            return null;
        }
        String last;
        do {
            last = list.get(list.size() - 1).toLowerCase(Locale.ROOT);
            if (TRAILING_NOISE_WORDS.contains(last)) {
                list.remove(list.size() - 1);
            } else {
                break;
            }
        } while (!list.isEmpty());
        if (list.isEmpty()) {
            return null;
        }
        return normalizeSingleQueryToken(String.join(" ", list));
    }

    private static String normalizeSingleQueryToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return null;
        }
        // English plural normalization: puzzles -> puzzle, batteries -> battery, boxes -> box
        if (!t.contains(" ")) {
            if (t.length() > 4 && t.endsWith("ies")) {
                t = t.substring(0, t.length() - 3) + "y";
            } else if (t.length() > 4 && t.endsWith("es")
                    && (t.endsWith("ses") || t.endsWith("xes") || t.endsWith("zes") || t.endsWith("ches") || t.endsWith("shes"))) {
                t = t.substring(0, t.length() - 2);
            } else if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss")) {
                t = t.substring(0, t.length() - 1);
            }
        }
        return t;
    }

    static boolean isWeakStandaloneQuery(String q) {
        if (q == null) {
            return false;
        }
        String t = q.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.contains(" ")) {
            return false;
        }
        return WEAK_STANDALONE_QUERIES.contains(t.toLowerCase(Locale.ROOT));
    }

    private static void sortSearchQueriesInPlace(List<String> queries) {
        queries.sort(Comparator
                .comparingInt((String q) -> isWeakStandaloneQuery(q) ? 1 : 0)
                .thenComparing(Comparator.comparingInt(String::length).reversed())
                .thenComparing(s -> s.toLowerCase(Locale.ROOT), String::compareTo)
        );
    }

    /**
     * Türkçe çoğul / hal ekleri (uzun → kısa sıra). "kitaplardan" → "kitap" gibi.
     */
    private static final String[] TURKISH_SUFFIXES = {
            "larından", "lerinden", "larında", "lerinde", "larına", "lerine", "larıyla", "leriyle",
            "lardan", "lerden", "ları", "leri", "lar", "ler",
            "ım", "im", "um", "üm", "ın", "in", "un", "ün",
            "dan", "den", "tan", "ten", "da", "de", "ta", "te",
            "ı", "i", "u", "ü", "a", "e"
    };

    private final ProductRepository productRepository;
    private final ProductInteractionRepository productInteractionRepository;
    private final ReviewRepository reviewRepository;
    private final TagRepository tagRepository;

    /**
     * AI-driven product feed: uses the search query extracted directly by the language model
     * instead of regex heuristics, plus applies a like-category boost to personalise results.
     *
     * @param aiQuery       search term provided by OpenAI (null means no search-based carousel)
     * @param preferHighRated true when the user explicitly wants highly-rated products
     * @param useUserLikes  true when the user explicitly asked for likes-based recommendations
     */
    public List<ChatProductCardDto> buildFeedWithAiIntent(
            SystemUser user,
            String aiQuery,
            boolean preferHighRated,
            boolean useUserLikes) {

        Set<Long> preferredTagIds = loadPreferredTagIds(user);

        if (aiQuery != null && !aiQuery.isBlank()) {
            List<ChatProductCardDto> fromSearch = buildFromSearchQuery(
                    aiQuery.trim(), aiQuery.trim(), preferHighRated, preferredTagIds);
            if (!fromSearch.isEmpty()) {
                return fromSearch;
            }
            if (useUserLikes && user instanceof GeneralUser gu) {
                return buildFromLikedTags(gu, preferHighRated);
            }
            return List.of();
        }

        if (useUserLikes && user instanceof GeneralUser gu) {
            return buildFromLikedTags(gu, preferHighRated);
        }

        return List.of();
    }

    /**
     * Mesaj ürün keşfi / öneri niyeti taşıyorsa gerçek ürün kartları üretir.
     * Öncelik: 1) Mesajdan çıkan arama terimi (TR/EN kalıplar + serbest kelimeler)
     * 2) Açıkça “beğendiğime göre” / “based on my likes” 3) Genel liste (yüksek puan istenmişse sıralama).
     */
    public List<ChatProductCardDto> buildFeed(SystemUser user, String userMessage) {
        return buildFeed(user, userMessage, null);
    }

    /**
     * @param conversationRetrievalText son turlar + güncel mesaj (ör. "user: …\\nassistant: …\\nuser: evet");
     *        arama ve telefon/saat/kitap niyeti buradan da çıkarılır; kısa onaylarda konu korunur.
     */
    public List<ChatProductCardDto> buildFeed(SystemUser user, String userMessage, String conversationRetrievalText) {
        String retrieval = (conversationRetrievalText != null && !conversationRetrievalText.isBlank())
                ? conversationRetrievalText
                : userMessage;
        boolean shortContinuation = isShortContinuation(userMessage);

        boolean showFeed = wantsProductFeed(userMessage)
                || (shortContinuation && wantsProductFeed(retrieval));
        if (!showFeed) {
            return List.of();
        }

        boolean preferHighRated = wantsHighRated(userMessage) || wantsHighRated(retrieval);

        // Normal sorgularda sadece güncel mesajı kullan; önceki turdaki "iphone" gibi niyetler
        // yeni isteği (ör. "suggest me a jacket") yanlış yönlendirmesin.
        String querySource = shortContinuation ? retrieval : userMessage;
        List<String> searchQueries = mergeQueriesWithIntent(querySource);
        if (!searchQueries.isEmpty()) {
            List<ChatProductCardDto> fromSearch = buildFromSearchQueries(searchQueries, querySource, preferHighRated);
            if (!fromSearch.isEmpty()) {
                return fromSearch;
            }
            return List.of();
        }

        boolean likesBasedAsk = wantsLikesBasedRecommendation(userMessage)
                || (shortContinuation && wantsLikesBasedRecommendation(retrieval));
        if (user instanceof GeneralUser gu && likesBasedAsk) {
            return buildFromLikedTags(gu, preferHighRated);
        }

        // Prefer likes-based carousel over a fully generic list when the user has liked products.
        if (user instanceof GeneralUser gu) {
            List<ChatProductCardDto> fromLikes = buildFromLikedTags(gu, preferHighRated);
            if (!fromLikes.isEmpty()) {
                return fromLikes;
            }
        }

        return buildGenericNewest(user, userMessage, preferHighRated);
    }

    /** Önce saat/telefon gibi sabit niyetler, sonra çıkarılmış terimler (tekrarsız). */
    private static List<String> mergeQueriesWithIntent(String message) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String s : intentBootstrapQueries(message)) {
            tryAddQuery(s, seen, out);
        }
        for (String s : extractSearchQueriesOrdered(message)) {
            tryAddQuery(s, seen, out);
        }
        return out;
    }

    private static List<String> intentBootstrapQueries(String message) {
        List<String> list = new ArrayList<>();
        String tr = message.toLowerCase(LOCALE_TR);
        String en = message.toLowerCase(Locale.ROOT);
        boolean watch = tr.contains("akıllı saat") || tr.contains("akilli saat")
                || en.contains("smartwatch") || en.contains("smart watch")
                || (tr.contains("saat") && (tr.contains("akıllı") || tr.contains("akilli")));
        boolean phone = !watch && (tr.contains("akıllı telefon") || tr.contains("akilli telefon")
                || tr.contains("cep telefon") || en.contains("smartphone") || en.contains("iphone")
                || tr.contains("iphone")
                || ((tr.contains("telefon") || (en.contains("phone") && !en.contains("headphone")
                && !en.contains("microphone") && !en.contains("smartwatch")))));
        if (watch) {
            list.add("smartwatch");
        } else if (phone) {
            list.add("smartphone");
        }
        boolean applePhone = phone && (en.contains("apple") || tr.contains("apple")
                || en.contains("iphone") || tr.contains("iphone"));
        if (applePhone) {
            list.add(0, "iphone");
        }
        boolean samsungPhone = phone && (en.contains("samsung") || tr.contains("samsung"));
        if (samsungPhone) {
            list.add(0, "samsung");
        }
        boolean oppoPhone = phone && (en.contains("oppo") || tr.contains("oppo"));
        if (oppoPhone) {
            list.add(0, "oppo");
        }
        boolean xiaomiPhone = phone && (en.contains("xiaomi") || tr.contains("xiaomi")
                || en.contains("redmi") || tr.contains("redmi"));
        if (xiaomiPhone) {
            list.add(0, "xiaomi");
        }
        boolean huaweiPhone = phone && (en.contains("huawei") || tr.contains("huawei"));
        if (huaweiPhone) {
            list.add(0, "huawei");
        }
        boolean book = !watch && !phone && (tr.contains("kitap") || EN_BOOK_WORD.matcher(en).find());
        if (book) {
            list.add("book");
        }
        return list;
    }

    /** Sırayla dene: ilk anlamlı sonuç dönene kadar. */
    private List<ChatProductCardDto> buildFromSearchQueries(List<String> queries, String userMessage, boolean preferHighRated) {
        return buildFromSearchQueries(queries, userMessage, preferHighRated, Set.of());
    }

    private List<ChatProductCardDto> buildFromSearchQueries(List<String> queries, String userMessage, boolean preferHighRated, Set<Long> preferredTagIds) {
        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            List<ChatProductCardDto> found = buildFromSearchQuery(q.trim(), userMessage, preferHighRated, preferredTagIds);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    /**
     * Önce tag alt ağacı (categoryPath prefix) ile daraltır; isim+açıklama LIKE ile kesiştirilir.
     * Saat/telefon niyetinde kitap vb. yanlış kategoriler skor ile elenir.
     */
    private List<ChatProductCardDto> buildFromSearchQuery(String q, String userMessage, boolean preferHighRated, Set<Long> preferredTagIds) {
        Set<Long> subtreeTagIds = resolveSubtreeTagIdsForLiteral(q);
        List<Long> tagList = new ArrayList<>(subtreeTagIds);
        if (tagList.size() > 200) {
            tagList = new ArrayList<>(tagList.subList(0, 200));
        }

        List<Long> ids = new ArrayList<>();
        if (!tagList.isEmpty()) {
            Page<Long> scoped = productRepository.searchProductIds(q, tagList, null, PageRequest.of(0, CANDIDATE_POOL));
            ids.addAll(scoped.getContent());
            if (ids.isEmpty()) {
                scoped = productRepository.searchProductIds(null, tagList, null, PageRequest.of(0, CANDIDATE_POOL));
                ids.addAll(scoped.getContent());
            }
        }

        if (ids.isEmpty()) {
            Page<Long> page = productRepository.searchProductIds(q, null, null, PageRequest.of(0, CANDIDATE_POOL));
            ids.addAll(page.getContent());
        }

        if (ids.isEmpty()) {
            List<Tag> tags = tagRepository.searchTagsByName(q);
            if (!tags.isEmpty()) {
                List<Long> fallbackTagIds = tags.stream().map(Tag::getId).distinct().limit(MAX_TAG_IDS).toList();
                Page<Long> page = productRepository.searchProductIds(null, fallbackTagIds, null, PageRequest.of(0, CANDIDATE_POOL));
                ids.addAll(page.getContent());
            }
        }

        if (ids.isEmpty()) {
            return List.of();
        }

        boolean watchAsk = messageImpliesWatch(userMessage);
        boolean phoneAsk = messageImpliesPhone(userMessage);

        List<Product> products = loadProductsPreservingOrder(ids);
        products.sort(Comparator.comparing((Product p) -> relevanceScore(p, q, watchAsk, phoneAsk) + likesBonus(p, preferredTagIds)).reversed());

        products = products.stream()
                .filter(p -> productMeetsQueryTextSpecificity(p, q))
                .collect(Collectors.toList());
        if (products.isEmpty()) {
            return List.of();
        }

        int minRelevance = resolveMinRelevance(watchAsk, phoneAsk, q);
        List<Product> filtered = products.stream()
                .filter(p -> relevanceScore(p, q, watchAsk, phoneAsk) >= minRelevance)
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            products = filtered;
        } else {
            List<Product> softFiltered = products.stream()
                    .filter(p -> relevanceScore(p, q, watchAsk, phoneAsk) > 0)
                    .collect(Collectors.toList());
            if (!softFiltered.isEmpty()) {
                products = softFiltered;
            } else {
                return List.of();
            }
        }

        products = enforceCategoryCoherence(products);
        if (products.size() < MIN_RESULTS_AFTER_GUARDRAILS) {
            return List.of();
        }

        return toCardList(products, preferHighRated);
    }

    /**
     * İlk adaylarda belirgin bir kategori baskınsa (aynı üst yol), karışık kategorileri eleyip
     * öneri kartlarını tek bağlamda tutar.
     */
    private static List<Product> enforceCategoryCoherence(List<Product> ranked) {
        if (ranked == null || ranked.size() < 4) {
            return ranked != null ? ranked : List.of();
        }
        int window = Math.min(12, ranked.size());
        Map<String, Integer> freq = new LinkedHashMap<>();
        int usable = 0;
        for (int i = 0; i < window; i++) {
            String anchor = categoryAnchor(ranked.get(i));
            if (anchor == null || anchor.isBlank()) {
                continue;
            }
            usable++;
            freq.put(anchor, freq.getOrDefault(anchor, 0) + 1);
        }
        if (usable < 3 || freq.isEmpty()) {
            return ranked;
        }
        String winner = null;
        int winnerCount = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > winnerCount) {
                winner = e.getKey();
                winnerCount = e.getValue();
            }
        }
        if (winner == null) {
            return ranked;
        }
        double share = (double) winnerCount / (double) usable;
        if (share < MIN_DOMINANT_CATEGORY_SHARE) {
            return ranked;
        }
        final String winnerAnchor = winner;

        List<Product> narrowed = ranked.stream()
                .filter(p -> winnerAnchor.equals(categoryAnchor(p)))
                .collect(Collectors.toList());
        return narrowed.size() >= MIN_RESULTS_AFTER_GUARDRAILS ? narrowed : ranked;
    }

    private static String categoryAnchor(Product p) {
        if (p == null || p.getTag() == null || p.getTag().getCategoryPath() == null) {
            return null;
        }
        String path = p.getTag().getCategoryPath().trim();
        if (path.isEmpty()) {
            return null;
        }
        String normalized = path.replace("/", ">");
        String[] parts = normalized.split(">");
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) {
            String t = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                cleaned.add(t);
            }
            if (cleaned.size() >= 2) {
                break;
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        return String.join(">", cleaned);
    }

    private static String productSearchBlob(Product p) {
        if (p == null) {
            return "";
        }
        String path = "";
        String tagName = "";
        if (p.getTag() != null) {
            if (p.getTag().getCategoryPath() != null) {
                path = p.getTag().getCategoryPath();
            }
            if (p.getTag().getName() != null) {
                tagName = p.getTag().getName();
            }
        }
        String name = p.getName() != null ? p.getName() : "";
        String desc = p.getDescription() != null ? p.getDescription() : "";
        return (path + " " + tagName + " " + name + " " + desc).toLowerCase(Locale.ROOT);
    }

    /**
     * Genel (tüm kategoriler): çok sözcüklü sorgularda zorunlu terimler; tek sözcükte marka/ürün adı
     * sinyali (iPhone, pantolon) blob’da yoksa dışla — jenerik niyet sözcükleri hariç.
     */
    private static boolean productMeetsQueryTextSpecificity(Product p, String q) {
        if (p == null || q == null) {
            return true;
        }
        String t = q.trim();
        if (t.isEmpty()) {
            return true;
        }
        if (t.contains(" ")) {
            String blob = productSearchBlob(p);
            for (String part : t.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (part.length() < 2) {
                    continue;
                }
                if (MULTIWORD_OPTIONAL_TOKENS.contains(part)) {
                    continue;
                }
                if (STOPWORDS_TOKENS.contains(part)) {
                    continue;
                }
                if (!blob.contains(part)) {
                    return false;
                }
            }
            return true;
        }
        String qL = t.toLowerCase(Locale.ROOT);
        if (GENERIC_INTENT_QUERIES.contains(qL)) {
            return true;
        }
        if (qL.length() < 4) {
            return true;
        }
        return productSearchBlob(p).contains(qL);
    }

    private static int countRequiredQueryTokensForMultiword(String q) {
        if (q == null || !q.contains(" ")) {
            return 0;
        }
        int n = 0;
        for (String part : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.length() < 2) {
                continue;
            }
            if (MULTIWORD_OPTIONAL_TOKENS.contains(part)) {
                continue;
            }
            if (STOPWORDS_TOKENS.contains(part)) {
                continue;
            }
            n++;
        }
        return n;
    }

    private static int resolveMinRelevance(boolean watchAsk, boolean phoneAsk, String q) {
        int base = (watchAsk || phoneAsk)
                ? MIN_RELEVANCE_FOR_STRICT_INTENT
                : MIN_RELEVANCE_FOR_GENERIC_QUERY;
        if (q != null && q.contains(" ") && countRequiredQueryTokensForMultiword(q) > 0) {
            return Math.max(base, MIN_RELEVANCE_FOR_MULTIWORD);
        }
        return base;
    }

    private Set<Long> resolveSubtreeTagIdsForLiteral(String literal) {
        Set<Long> out = new LinkedHashSet<>();
        if (literal == null || literal.isBlank()) {
            return out;
        }
        for (Tag t : tagRepository.searchTagsByName(literal.trim())) {
            if (t.getCategoryPath() != null && !t.getCategoryPath().isBlank()) {
                out.addAll(tagRepository.findActiveTagIdsUnderPathPrefix(t.getCategoryPath()));
            } else {
                out.add(t.getId());
            }
            if (out.size() > 250) {
                break;
            }
        }
        return out;
    }

    private static boolean messageImpliesWatch(String message) {
        if (message == null) {
            return false;
        }
        String tr = message.toLowerCase(LOCALE_TR);
        String en = message.toLowerCase(Locale.ROOT);
        return tr.contains("akıllı saat") || tr.contains("akilli saat")
                || en.contains("smartwatch") || en.contains("smart watch")
                || (tr.contains("saat") && (tr.contains("akıllı") || tr.contains("akilli")));
    }

    private static boolean messageImpliesPhone(String message) {
        if (message == null) {
            return false;
        }
        if (messageImpliesWatch(message)) {
            return false;
        }
        String tr = message.toLowerCase(LOCALE_TR);
        String en = message.toLowerCase(Locale.ROOT);
        return tr.contains("akıllı telefon") || tr.contains("akilli telefon") || tr.contains("cep telefon")
                || en.contains("smartphone") || en.contains("iphone") || tr.contains("iphone")
                || tr.contains("telefon")
                || (en.contains("phone") && !en.contains("headphone") && !en.contains("microphone"));
    }

    /**
     * Bonus score for products in a category the user has previously liked.
     * Applied on top of text-based relevance so liked-category products surface first.
     */
    private static int likesBonus(Product p, Set<Long> preferredTagIds) {
        if (preferredTagIds.isEmpty() || p == null || p.getTag() == null) {
            return 0;
        }
        return preferredTagIds.contains(p.getTag().getId()) ? 20 : 0;
    }

    private static int relevanceScore(Product p, String q, boolean watchAsk, boolean phoneAsk) {
        String path = "";
        String tagName = "";
        if (p.getTag() != null) {
            if (p.getTag().getCategoryPath() != null) {
                path = p.getTag().getCategoryPath().toLowerCase(Locale.ROOT);
            }
            if (p.getTag().getName() != null) {
                tagName = p.getTag().getName().toLowerCase(Locale.ROOT);
            }
        }
        String name = p.getName() != null ? p.getName().toLowerCase(Locale.ROOT) : "";
        String desc = p.getDescription() != null ? p.getDescription().toLowerCase(Locale.ROOT) : "";
        String qL = q.toLowerCase(Locale.ROOT);

        if (watchAsk && (path.contains("book") || path.contains("clothing"))) {
            return -1000;
        }
        if (phoneAsk && (path.contains("book") || path.contains("clothing"))) {
            return -1000;
        }
        if (phoneAsk && (path.contains("tablet") || path.contains("ipad") || tagName.contains("tablet"))) {
            if (!name.contains("iphone") && !name.contains("galaxy s") && !name.contains("pixel")) {
                return -300;
            }
        }
        if (phoneAsk && looksLikePhoneAccessory(path, tagName, name)) {
            return -450;
        }
        if (phoneAsk && (path.contains("headphone") || path.contains("airpod") || path.contains("earbud")
                || path.contains("beats"))) {
            return -300;
        }
        if (watchAsk && path.contains("watch")) {
            return 100 + (name.contains(qL) ? 40 : 0);
        }
        if (phoneAsk && path.contains("smartphone")) {
            return 100 + (name.contains(qL) ? 40 : 0);
        }

        int s = 0;
        if (path.contains(qL)) {
            s += 60;
        }
        if (tagName.contains(qL)) {
            s += 50;
        }
        if (name.contains(qL)) {
            s += 35;
        }
        if (desc.contains(qL)) {
            s += 10;
        }
        return s;
    }

    /**
     * iPhone/telefon isteğinde "uyumlu aksesuar" ürünlerini (kılıf, şarj vb.) aşağı iter.
     */
    private static boolean looksLikePhoneAccessory(String path, String tagName, String name) {
        String joined = (path + " " + tagName + " " + name).toLowerCase(Locale.ROOT);
        if (joined.isBlank()) {
            return false;
        }
        String[] accessoryWords = {
                "accessory", "aksesuar", "case", "cover", "kılıf", "kilif", "charger", "şarj", "sarj",
                "cable", "kablo", "adapter", "adaptor", "earbud", "airpod", "headphone", "kulaklık", "kulaklik"
        };
        for (String w : accessoryWords) {
            if (joined.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Sadece kullanıcı açıkça “beğendiklerime göre” dediyse */
    private List<ChatProductCardDto> buildFromLikedTags(GeneralUser gu, boolean preferHighRated) {
        Set<Long> likedIds = loadLikedProductIds(gu);
        List<Long> preferredTagIds = loadPreferredTagIdsFromLikes(gu);

        if (preferredTagIds.isEmpty()) {
            return List.of();
        }

        // Fetch a larger pool across multiple pages so repeated requests surface different products.
        int poolPerPage = CANDIDATE_POOL;
        int pagesToFetch = 3;
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (int p = 0; p < pagesToFetch; p++) {
            Page<Long> page = productRepository.searchProductIds(
                    null, preferredTagIds, null, PageRequest.of(p, poolPerPage));
            seen.addAll(page.getContent());
            if (page.getContent().size() < poolPerPage) {
                break;
            }
        }

        List<Long> candidateIds = seen.stream()
                .filter(id -> !likedIds.contains(id))
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        // Shuffle so each request surfaces a different slice of the pool.
        // preferHighRated will re-sort after shuffling in toCardList.
        Collections.shuffle(candidateIds);

        List<Product> products = loadProductsPreservingOrder(candidateIds);
        return toCardList(products, preferHighRated);
    }

    /**
     * Genel öneri: en yeni ürünler; sayfa offset’i mesaj+kullanıcı hash’i ile değişir (aynı listenin tekrarı azalır).
     */
    private List<ChatProductCardDto> buildGenericNewest(SystemUser user, String userMessage, boolean preferHighRated) {
        int pages = 5;
        int pageHint = Math.floorMod(Objects.hash(userMessage, user.getId()), pages);
        List<Long> candidateIds = newestActiveProductIds(CANDIDATE_POOL, pageHint);
        List<Product> ordered = loadProductsPreservingOrder(candidateIds);
        return toCardList(ordered, preferHighRated);
    }

    private List<Long> newestActiveProductIds(int limit, int page) {
        return productRepository
                .findActiveProductIdsOrderByCreatedAtDescIdAsc(PageRequest.of(page, limit))
                .getContent();
    }

    private Set<Long> loadLikedProductIds(GeneralUser gu) {
        var page = productInteractionRepository.findLikedProductsByPerformerId(
                gu.getId(),
                PageRequest.of(0, MAX_LIKES_FOR_TAGS)
        );
        Set<Long> ids = new HashSet<>();
        for (ProductInteraction pi : page.getContent()) {
            if (pi.getTargetProduct() != null) {
                ids.add(pi.getTargetProduct().getId());
            }
        }
        return ids;
    }

    /** Returns the preferred tag IDs for the current user (empty set for non-GeneralUsers). */
    private Set<Long> loadPreferredTagIds(SystemUser user) {
        if (!(user instanceof GeneralUser gu)) {
            return Set.of();
        }
        return new HashSet<>(loadPreferredTagIdsFromLikes(gu));
    }

        private List<Long> loadPreferredTagIdsFromLikes(GeneralUser gu) {
        var page = productInteractionRepository.findLikedProductsByPerformerId(
                gu.getId(),
                PageRequest.of(0, MAX_LIKES_FOR_TAGS)
        );
        Map<Long, Integer> tagFreq = new LinkedHashMap<>();
        int total = 0;
        for (ProductInteraction pi : page.getContent()) {
            Product p = pi.getTargetProduct();
            if (p != null && p.getTag() != null && Boolean.TRUE.equals(p.getIsActive())) {
                Long tagId = p.getTag().getId();
                tagFreq.put(tagId, tagFreq.getOrDefault(tagId, 0) + 1);
                total++;
            }
        }
        if (tagFreq.isEmpty()) {
            return List.of();
        }

        final int totalLikes = Math.max(total, 1);
        List<Map.Entry<Long, Integer>> sorted = tagFreq.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .toList();

        List<Long> selected = sorted.stream()
                .filter(e -> e.getValue() >= 2 || ((double) e.getValue() / (double) totalLikes) >= 0.34)
                .map(Map.Entry::getKey)
                .limit(MAX_PREFERRED_TAGS_FROM_LIKES)
                .toList();
        if (!selected.isEmpty()) {
            return selected;
        }
        return sorted.stream()
                .map(Map.Entry::getKey)
                .limit(1)
                .toList();
    }

    private List<Product> loadProductsPreservingOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> found = productRepository.findByIdInWithTagAndParent(ids);
        Map<Long, Product> byId = found.stream().collect(Collectors.toMap(Product::getId, p -> p));
        return new ArrayList<>(ids.stream().map(byId::get).filter(Objects::nonNull).toList());
    }

    private List<ChatProductCardDto> toCardList(List<Product> products, boolean preferHighRated) {
        List<Product> work = new ArrayList<>(products);
        if (preferHighRated) {
            work.sort(Comparator.comparing((Product p) ->
                    Optional.ofNullable(reviewRepository.calculateAverageRatingByProductId(p.getId())).orElse(0.0)
            ).reversed());
        }
        return work.stream().limit(FEED_SIZE).map(this::toCard).toList();
    }

    private ChatProductCardDto toCard(Product p) {
        Long pid = p.getId();
        Double avg = reviewRepository.calculateAverageRatingByProductId(pid);
        Long cnt = reviewRepository.countReviewsByProductId(pid);
        String tagName = p.getTag() != null ? p.getTag().getName() : null;
        return new ChatProductCardDto(pid, p.getName(), p.getImageURL(), tagName, avg, cnt);
    }

    /**
     * Arama için aday sorgular (öncelik sırasıyla). Bilinen eş anlamlılar + kalıplar + serbest kelime kökleri.
     */
    static List<String> extractSearchQueriesOrdered(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        String m = message.toLowerCase(LOCALE_TR);

        List<Map.Entry<String, String>> entries = new ArrayList<>(PHRASE_TO_SEARCH_Q.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String, String> e : entries) {
            if (m.contains(e.getKey().toLowerCase(LOCALE_TR))) {
                tryAddQuery(e.getValue(), seen, out);
                break;
            }
        }

        Matcher mc = WORD_BEFORE_KATEGORI.matcher(message);
        if (mc.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(mc.group(1)), seen, out);
        }
        Matcher mk = KATEGORI_PREFIX.matcher(message);
        if (mk.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(mk.group(1)), seen, out);
        }
        Matcher ma = ABLATIVE_WORD_THEN_VERB.matcher(message);
        if (ma.find()) {
            tryAddQuery(stripCommonTurkishSuffix(ma.group(1).toLowerCase(LOCALE_TR)), seen, out);
        }
        Matcher mv = VERB_THEN_NOUN.matcher(message);
        if (mv.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(mv.group(1)), seen, out);
        }
        Matcher mb = BANA_NOUN_VERB.matcher(message);
        if (mb.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(mb.group(1)), seen, out);
        }

        Matcher enc = EN_CATEGORY_PREFIX.matcher(message);
        if (enc.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(enc.group(1)), seen, out);
        }
        Matcher ein = EN_IN_THE_CATEGORY.matcher(message);
        if (ein.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(ein.group(1)), seen, out);
        }
        Matcher er = EN_RECOMMEND_SUGGEST.matcher(message);
        if (er.find()) {
            tryAddQuery(normalizeQueryPhrase(er.group(1)), seen, out);
        }
        Matcher es = EN_SHOW_ME.matcher(message);
        if (es.find()) {
            tryAddQuery(normalizeQueryPhrase(es.group(1)), seen, out);
        }
        Matcher enp = EN_NOUN_PRODUCTS.matcher(message);
        if (enp.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(enp.group(1)), seen, out);
        }
        Matcher ef = EN_FIND_SEARCH.matcher(message);
        if (ef.find()) {
            tryAddQuery(normalizeQueryPhrase(ef.group(1)), seen, out);
        }

        if (messageSuggestsOpenCategoryBrowse(message)) {
            for (String stem : extractStemmedTokenCandidates(message)) {
                tryAddQuery(stem, seen, out);
            }
        }

        sortSearchQueriesInPlace(out);
        return out;
    }

    private static void tryAddQuery(String q, LinkedHashSet<String> seen, List<String> out) {
        if (q == null || q.isBlank()) {
            return;
        }
        String t = q.trim();
        if (t.length() < 2) {
            return;
        }
        String norm = t.toLowerCase(Locale.ROOT);
        if (seen.add(norm)) {
            out.add(t);
        }
    }

    private static String normalizeCapturedCategoryWord(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String w = normalizeSingleQueryToken(raw.toLowerCase(LOCALE_TR).trim());
        if (w == null || w.isBlank()) {
            return null;
        }
        if (STOPWORDS_FOR_CATEGORY.contains(w) || STOPWORDS_TOKENS.contains(w)) {
            return null;
        }
        return stripCommonTurkishSuffix(w);
    }

    private static String stripCommonTurkishSuffix(String w) {
        if (w == null || w.length() < 3) {
            return w;
        }
        String t = w.toLowerCase(LOCALE_TR);
        for (String suf : TURKISH_SUFFIXES) {
            if (t.length() > suf.length() + 2 && t.endsWith(suf)) {
                return t.substring(0, t.length() - suf.length());
            }
        }
        return t;
    }

    /** Ürün/kategori listeleme niyeti (Türkçe + İngilizce) — token taraması için. */
    private static boolean messageSuggestsOpenCategoryBrowse(String message) {
        String tr = message.toLowerCase(LOCALE_TR);
        String en = message.toLowerCase(Locale.ROOT);
        if (tr.contains("öner") || tr.contains("oner") || tr.contains("getir")
                || tr.contains("göster") || tr.contains("goster") || tr.contains("listele")
                || tr.contains("ürün") || tr.contains("urun") || tr.contains("kategori")
                || tr.contains(" bul ") || tr.startsWith("bul ") || tr.endsWith(" bul")
                || tr.contains(" ara ") || tr.startsWith("ara ") || tr.endsWith(" ara")) {
            return true;
        }
        return en.contains("recommend") || en.contains("suggest") || en.contains("browse")
                || en.contains("category") || en.contains("product") || en.contains("catalog")
                || en.contains("catalogue") || en.contains("shop") || en.contains("shopping")
                || en.contains(" list ") || en.startsWith("list ") || en.endsWith(" list")
                || en.contains(" show ") || en.startsWith("show ") || en.contains("find ")
                || en.contains("search ") || en.contains("display") || en.contains("anything")
                || en.contains("rated") || en.contains("rating") || en.contains("review");
    }

    /**
     * Mesaj kelimeleri + Türkçe ek kırpılmış kökler; uzun olanlar önce (LIKE isabeti için).
     */
    private static List<String> extractStemmedTokenCandidates(String message) {
        String[] parts = message.split("[^\\p{L}\\p{N}]+");
        List<String> variants = new ArrayList<>();
        for (String p : parts) {
            if (p == null || p.length() < 2) {
                continue;
            }
            String w = p.toLowerCase(LOCALE_TR);
            if (STOPWORDS_TOKENS.contains(w)) {
                continue;
            }
            variants.add(w);
            String stem = stripCommonTurkishSuffix(w);
            if (!stem.equals(w) && stem.length() >= 3 && !STOPWORDS_TOKENS.contains(stem)) {
                variants.add(stem);
            }
        }
        variants.sort((a, b) -> Integer.compare(b.length(), a.length()));
        LinkedHashSet<String> unique = new LinkedHashSet<>(variants);
        return new ArrayList<>(unique);
    }

    static boolean wantsLikesBasedRecommendation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(LOCALE_TR);
        String e = message.toLowerCase(Locale.ROOT);
        return m.contains("beğendiğim") || m.contains("beğendiklerim") || m.contains("beğendiklerime")
                || m.contains("begen digim") || m.contains("begen diklerim")
                || m.contains("favorilerim") || m.contains("favorilerime")
                || m.contains("ilgi alanım") || m.contains("ilgi alanlarım")
                || m.contains("ilgi alanima gore") || m.contains("ilgime göre")
                || m.contains("wishlist") || m.contains("wish list")
                || (m.contains("beğen") && m.contains("göre"))
                || (m.contains("begen") && m.contains("gore"))
                || e.contains("based on my likes") || e.contains("from my likes")
                || e.contains("from my favorites") || e.contains("from my favourites")
                || e.contains("like my favorites") || e.contains("similar to what i liked")
                || e.contains("my interests") || e.contains("according to my interests")
                || e.contains("based on my interests") || e.contains("based on my history");
    }

    private static final Pattern SHORT_CONTINUATION_TR_EN = Pattern.compile(
            "^(evet|evt|ha|hı|hi|eh|tabii|tabi|tamam|olur|peki|eyv|eyvallah|sağol|sagol|ok|okay|okey|k\\.|k\\b|yep|yup|yeah|yes|sure|please|thanks|thank\\s+you|go\\s+on|go\\s+ahead|continue|more\\s+please|sounds\\s+good|that\\s+works|alright|fine|great|perfect)"
                    + "(\\s+(please|thanks|olur|tabii|tabi))?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SHORT_CONTINUATION_PHRASE = Pattern.compile(
            "^(daha\\s+fazla|devam|aynı|ayni|başka|baska)(\\s+\\p{L}+){0,4}$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static boolean isGreetingOnly(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[.!?,;:…]+$", "")
                .trim();
        if (m.isEmpty()) {
            return false;
        }
        return GREETING_ONLY_WORDS.contains(m);
    }

    /**
     * Kısa onay / devam (“evet”, “yes”, “tamam”); önceki turda ürün niyeti varsa {@link #buildFeed} retrieval ile birleştirir.
     */
    static boolean isShortContinuation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (isGreetingOnly(message)) {
            return false;
        }
        String m = message.trim().replaceAll("[.!?…]+$", "").trim();
        if (m.length() > 72) {
            return false;
        }
        return SHORT_CONTINUATION_TR_EN.matcher(m).matches()
                || SHORT_CONTINUATION_PHRASE.matcher(m).matches();
    }

    static boolean wantsProductFeed(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String t = message.toLowerCase(LOCALE_TR);
        String e = message.toLowerCase(Locale.ROOT);
        String[] tr = {
                "ürün", "urun", "öner", "oner", "beğen", "begen", "kategori", "puan", "keşfet", "kesfet",
                "popüler", "populer", "hangi", "göster", "goster", "listele", "başka", "baska", "tavsiye",
                "almalı", "almali", "satın", "satin", "fiyat", "yorumlu", "inceleme", "getir", "bul", "ara"
        };
        for (String k : tr) {
            if (t.contains(k)) {
                return true;
            }
        }
        String[] en = {
                "product", "products", "recommend", "suggestion", "suggest", "category", "categories",
                "browse", "discover", "wishlist", "wish list", "rated", "rating", "review", "reviews",
                "show me", "list ", " list", "shopping", "shop for", "catalog", "catalogue", "search for",
                "find ", "looking for", "top rated", "high rated", "best rated", "similar to"
        };
        for (String k : en) {
            if (e.contains(k)) {
                return true;
            }
        }
        return false;
    }

    static boolean wantsHighRated(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String tr = message.toLowerCase(LOCALE_TR);
        String e = message.toLowerCase(Locale.ROOT);
        return tr.contains("yüksek puan") || tr.contains("yuksek puan")
                || tr.contains("en yüksek") || tr.contains("en yuksek")
                || tr.contains("en iyi") || tr.contains("en çok") || tr.contains("en cok")
                || tr.contains("yüksek oy") || tr.contains("yuksek oy")
                || tr.contains("en çok oy") || tr.contains("en cok oy")
                || e.contains("high rated") || e.contains("highly rated") || e.contains("highest rated")
                || e.contains("top rated") || e.contains("best rated") || e.contains("best reviewed")
                || e.contains("five star") || e.contains("5 star") || e.contains("5 stars")
                || e.contains("sort by rating") || e.contains("by rating") || e.contains("well reviewed")
                || e.contains("star rating") || e.contains("good reviews") || e.contains("great reviews");
    }
}
