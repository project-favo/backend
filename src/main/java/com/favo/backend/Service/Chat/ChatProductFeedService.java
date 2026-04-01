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
    /** recommend books, suggest me headphones */
    private static final Pattern EN_RECOMMEND_SUGGEST = Pattern.compile(
            "(?:recommend|suggest)(?:\\s+me|\\s+us)?\\s+([\\p{L}\\p{N}]{2,})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** show me laptops, show headphones */
    private static final Pattern EN_SHOW_ME = Pattern.compile(
            "show\\s+(?:me\\s+|us\\s+)?([\\p{L}\\p{N}]{2,})(?:\\s+products?)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "book products" — en az 3 harf; "rated products" gibi gürültü stopword ile elenir */
    private static final Pattern EN_NOUN_PRODUCTS = Pattern.compile(
            "\\b([\\p{L}\\p{N}]{3,})\\s+products?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** find laptops, search watches */
    private static final Pattern EN_FIND_SEARCH = Pattern.compile(
            "(?:find|search|browse|list)\\s+(?:for\\s+)?([\\p{L}\\p{N}]{2,})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

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
     * Mesaj ürün keşfi / öneri niyeti taşıyorsa gerçek ürün kartları üretir.
     * Öncelik: 1) Mesajdan çıkan arama terimi (TR/EN kalıplar + serbest kelimeler)
     * 2) Açıkça “beğendiğime göre” / “based on my likes” 3) Genel liste (yüksek puan istenmişse sıralama).
     */
    public List<ChatProductCardDto> buildFeed(SystemUser user, String userMessage) {
        if (!wantsProductFeed(userMessage)) {
            return List.of();
        }

        boolean preferHighRated = wantsHighRated(userMessage);

        List<String> searchQueries = mergeQueriesWithIntent(userMessage);
        if (!searchQueries.isEmpty()) {
            List<ChatProductCardDto> fromSearch = buildFromSearchQueries(searchQueries, userMessage, preferHighRated);
            if (!fromSearch.isEmpty()) {
                return fromSearch;
            }
            return List.of();
        }

        if (user instanceof GeneralUser gu && wantsLikesBasedRecommendation(userMessage)) {
            return buildFromLikedTags(gu, preferHighRated);
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
        boolean applePhone = phone && (en.contains("apple") || tr.contains("apple"));
        if (applePhone) {
            list.add(0, "iphone");
        }
        return list;
    }

    /** Sırayla dene: ilk anlamlı sonuç dönene kadar. */
    private List<ChatProductCardDto> buildFromSearchQueries(List<String> queries, String userMessage, boolean preferHighRated) {
        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            List<ChatProductCardDto> found = buildFromSearchQuery(q.trim(), userMessage, preferHighRated);
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
    private List<ChatProductCardDto> buildFromSearchQuery(String q, String userMessage, boolean preferHighRated) {
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
        products.sort(Comparator.comparing((Product p) -> relevanceScore(p, q, watchAsk, phoneAsk)).reversed());

        if (watchAsk || phoneAsk) {
            List<Product> filtered = products.stream()
                    .filter(p -> relevanceScore(p, q, watchAsk, phoneAsk) > -500)
                    .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                products = filtered;
            }
        }

        return toCardList(products, preferHighRated);
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

    /** Sadece kullanıcı açıkça “beğendiklerime göre” dediyse */
    private List<ChatProductCardDto> buildFromLikedTags(GeneralUser gu, boolean preferHighRated) {
        Set<Long> likedIds = loadLikedProductIds(gu);
        Set<Long> tagIds = loadPreferredTagIdsFromLikes(gu);

        List<Long> candidateIds;
        if (tagIds.isEmpty()) {
            candidateIds = newestActiveProductIds(CANDIDATE_POOL, 0);
        } else {
            Page<Long> page = productRepository.searchProductIds(
                    null,
                    new ArrayList<>(tagIds),
                    null,
                    PageRequest.of(0, CANDIDATE_POOL)
            );
            candidateIds = page.getContent().stream()
                    .filter(id -> !likedIds.contains(id))
                    .toList();
            if (candidateIds.isEmpty()) {
                candidateIds = newestActiveProductIds(CANDIDATE_POOL, 0);
            }
        }

        List<Product> ordered = loadProductsPreservingOrder(candidateIds);
        return toCardList(ordered, preferHighRated);
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

    private Set<Long> loadPreferredTagIdsFromLikes(GeneralUser gu) {
        var page = productInteractionRepository.findLikedProductsByPerformerId(
                gu.getId(),
                PageRequest.of(0, MAX_LIKES_FOR_TAGS)
        );
        Set<Long> tagIds = new HashSet<>();
        for (ProductInteraction pi : page.getContent()) {
            Product p = pi.getTargetProduct();
            if (p != null && p.getTag() != null && Boolean.TRUE.equals(p.getIsActive())) {
                tagIds.add(p.getTag().getId());
            }
        }
        return tagIds;
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
            tryAddQuery(normalizeCapturedCategoryWord(er.group(1)), seen, out);
        }
        Matcher es = EN_SHOW_ME.matcher(message);
        if (es.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(es.group(1)), seen, out);
        }
        Matcher enp = EN_NOUN_PRODUCTS.matcher(message);
        if (enp.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(enp.group(1)), seen, out);
        }
        Matcher ef = EN_FIND_SEARCH.matcher(message);
        if (ef.find()) {
            tryAddQuery(normalizeCapturedCategoryWord(ef.group(1)), seen, out);
        }

        if (messageSuggestsOpenCategoryBrowse(message)) {
            for (String stem : extractStemmedTokenCandidates(message)) {
                tryAddQuery(stem, seen, out);
            }
        }

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
        String w = raw.toLowerCase(LOCALE_TR).trim();
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
                || m.contains("wishlist") || m.contains("wish list")
                || (m.contains("beğen") && m.contains("göre"))
                || (m.contains("begen") && m.contains("gore"))
                || e.contains("based on my likes") || e.contains("from my likes")
                || e.contains("from my favorites") || e.contains("from my favourites")
                || e.contains("like my favorites") || e.contains("similar to what i liked");
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
