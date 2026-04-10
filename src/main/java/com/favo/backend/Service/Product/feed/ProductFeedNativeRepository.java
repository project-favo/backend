package com.favo.backend.Service.Product.feed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Trend / kişisel feed için aggregate native sorgular (sayfalı ürün id + skor).
 */
@Repository
public class ProductFeedNativeRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public long countProductsByReviewWindow(LocalDateTime start, LocalDateTime endExclusive) {
        String sql = """
                SELECT COUNT(*) FROM (
                  SELECT r.product_id
                  FROM review r
                  INNER JOIN product p ON p.id = r.product_id AND p.is_active = true
                  WHERE r.is_active = true
                    AND r.created_at >= :start
                    AND r.created_at < :endExclusive
                  GROUP BY r.product_id
                ) x
                """;
        Object single = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("endExclusive", endExclusive)
                .getSingleResult();
        return ((Number) single).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<ScoredProductId> findProductIdsByReviewWindow(
            LocalDateTime start,
            LocalDateTime endExclusive,
            int offset,
            int limit
    ) {
        String sql = """
                SELECT r.product_id, COUNT(r.id) AS score
                FROM review r
                INNER JOIN product p ON p.id = r.product_id AND p.is_active = true
                WHERE r.is_active = true
                  AND r.created_at >= :start
                  AND r.created_at < :endExclusive
                GROUP BY r.product_id
                ORDER BY score DESC, r.product_id ASC
                LIMIT :limit OFFSET :offset
                """;
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("endExclusive", endExclusive);
        q.setParameter("limit", limit);
        q.setParameter("offset", offset);
        return mapScoredRows(q.getResultList());
    }

    @SuppressWarnings("unchecked")
    public long countProductsByWeeklyLikes(LocalDateTime start, LocalDateTime endExclusive) {
        String sql = """
                SELECT COUNT(*) FROM (
                  SELECT pi.target_product_id
                  FROM product_interaction pi
                  INNER JOIN interaction i ON i.id = pi.interaction_id
                  INNER JOIN product p ON p.id = pi.target_product_id AND p.is_active = true
                  WHERE pi.type = 'LIKE'
                    AND i.is_active = true
                    AND i.dtype = 'PRODUCT_INTERACTION'
                    AND i.created_at >= :start
                    AND i.created_at < :endExclusive
                  GROUP BY pi.target_product_id
                ) x
                """;
        Object single = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("endExclusive", endExclusive)
                .getSingleResult();
        return ((Number) single).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<ScoredProductId> findProductIdsByWeeklyLikes(
            LocalDateTime start,
            LocalDateTime endExclusive,
            int offset,
            int limit
    ) {
        String sql = """
                SELECT pi.target_product_id, COUNT(*) AS score
                FROM product_interaction pi
                INNER JOIN interaction i ON i.id = pi.interaction_id
                INNER JOIN product p ON p.id = pi.target_product_id AND p.is_active = true
                WHERE pi.type = 'LIKE'
                  AND i.is_active = true
                  AND i.dtype = 'PRODUCT_INTERACTION'
                  AND i.created_at >= :start
                  AND i.created_at < :endExclusive
                GROUP BY pi.target_product_id
                ORDER BY score DESC, pi.target_product_id ASC
                LIMIT :limit OFFSET :offset
                """;
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("endExclusive", endExclusive);
        q.setParameter("limit", limit);
        q.setParameter("offset", offset);
        return mapScoredRows(q.getResultList());
    }

    @SuppressWarnings("unchecked")
    public List<Long> findDistinctTagIdsFromLikes(Long userId) {
        String sql = """
                SELECT DISTINCT p.tag_id
                FROM product_interaction pi
                INNER JOIN interaction i ON i.id = pi.interaction_id
                INNER JOIN product p ON p.id = pi.target_product_id AND p.is_active = true
                WHERE i.performer_id = :userId
                  AND pi.type = 'LIKE'
                  AND i.is_active = true
                  AND i.dtype = 'PRODUCT_INTERACTION'
                """;
        List<?> rows = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getResultList();
        List<Long> out = new ArrayList<>();
        for (Object row : rows) {
            if (row != null) {
                out.add(((Number) row).longValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Long> findDistinctTagIdsFromReviews(Long userId) {
        String sql = """
                SELECT DISTINCT p.tag_id
                FROM review r
                INNER JOIN product p ON p.id = r.product_id AND p.is_active = true
                WHERE r.owner_id = :userId
                  AND r.is_active = true
                """;
        List<?> rows = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getResultList();
        List<Long> out = new ArrayList<>();
        for (Object row : rows) {
            if (row != null) {
                out.add(((Number) row).longValue());
            }
        }
        return out;
    }

    public long countPersonalizedReviewWindow(
            LocalDateTime start,
            LocalDateTime endExclusive,
            Collection<Long> leafTagIds,
            Collection<Long> parentTagIds
    ) {
        String sql = buildPersonalizedCountSql(leafTagIds, parentTagIds);
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("endExclusive", endExclusive);
        if (!leafTagIds.isEmpty()) {
            q.setParameter("leafIds", leafTagIds);
        }
        if (!parentTagIds.isEmpty()) {
            q.setParameter("parentIds", parentTagIds);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<ScoredProductId> findPersonalizedReviewWindow(
            LocalDateTime start,
            LocalDateTime endExclusive,
            Collection<Long> leafTagIds,
            Collection<Long> parentTagIds,
            int offset,
            int limit
    ) {
        String sql = buildPersonalizedPageSql(leafTagIds, parentTagIds);
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("endExclusive", endExclusive);
        if (!leafTagIds.isEmpty()) {
            q.setParameter("leafIds", leafTagIds);
        }
        if (!parentTagIds.isEmpty()) {
            q.setParameter("parentIds", parentTagIds);
        }
        q.setParameter("limit", limit);
        q.setParameter("offset", offset);
        return mapScoredRows(q.getResultList());
    }

    private static String buildPersonalizedFilter(Collection<Long> leafTagIds, Collection<Long> parentTagIds) {
        boolean useLeaf = leafTagIds != null && !leafTagIds.isEmpty();
        boolean useParent = parentTagIds != null && !parentTagIds.isEmpty();
        if (!useLeaf && !useParent) {
            return "";
        }
        if (useLeaf && useParent) {
            return " AND (p.tag_id IN (:leafIds) OR t.parent_id IN (:parentIds))";
        }
        if (useLeaf) {
            return " AND p.tag_id IN (:leafIds)";
        }
        return " AND t.parent_id IN (:parentIds)";
    }

    private static String buildPersonalizedCountSql(Collection<Long> leafTagIds, Collection<Long> parentTagIds) {
        String filter = buildPersonalizedFilter(leafTagIds, parentTagIds);
        return """
                SELECT COUNT(*) FROM (
                  SELECT r.product_id
                  FROM review r
                  INNER JOIN product p ON p.id = r.product_id AND p.is_active = true
                  INNER JOIN tag t ON t.id = p.tag_id
                  WHERE r.is_active = true
                    AND r.created_at >= :start
                    AND r.created_at < :endExclusive
                """ + filter + """
                  GROUP BY r.product_id
                ) x
                """;
    }

    private static String buildPersonalizedPageSql(Collection<Long> leafTagIds, Collection<Long> parentTagIds) {
        String filter = buildPersonalizedFilter(leafTagIds, parentTagIds);
        return """
                SELECT r.product_id, COUNT(r.id) AS score
                FROM review r
                INNER JOIN product p ON p.id = r.product_id AND p.is_active = true
                INNER JOIN tag t ON t.id = p.tag_id
                WHERE r.is_active = true
                  AND r.created_at >= :start
                  AND r.created_at < :endExclusive
                """ + filter + """
                GROUP BY r.product_id
                ORDER BY score DESC, r.product_id ASC
                LIMIT :limit OFFSET :offset
                """;
    }

    private static List<ScoredProductId> mapScoredRows(List<?> rows) {
        List<ScoredProductId> out = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Object[] arr = (Object[]) row;
            long pid = ((Number) arr[0]).longValue();
            long score = ((Number) arr[1]).longValue();
            out.add(new ScoredProductId(pid, score));
        }
        return out;
    }
}
