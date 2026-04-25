package com.favo.backend.Domain.product.Repository;

import com.favo.backend.Domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByIsActiveTrue();
    List<Product> findByTagIdAndIsActiveTrue(Long tagId);
    Optional<Product> findByIdAndIsActiveTrue(Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.tag t LEFT JOIN FETCH t.parent WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findByIdAndIsActiveTrueWithTag(@Param("id") Long id);

    /**
     * Ana sayfa: Sayfa için sadece ID listesi (DISTINCT+FETCH sayfa kaymasını önlemek için).
     * Sıra sabit: createdAt DESC, id ASC.
     */
    @Query(value = "SELECT p.id FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC, p.id ASC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    Page<Long> findActiveProductIdsOrderByCreatedAtDescIdAsc(Pageable pageable);

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "ORDER BY COALESCE(rs.avg_rating, 0) DESC, p.id ASC",
           countQuery = "SELECT COUNT(*) FROM product WHERE is_active = true",
           nativeQuery = true)
    Page<Long> findActiveProductIdsOrderByRatingDesc(Pageable pageable);

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "ORDER BY COALESCE(rs.avg_rating, 0) ASC, p.id ASC",
           countQuery = "SELECT COUNT(*) FROM product WHERE is_active = true",
           nativeQuery = true)
    Page<Long> findActiveProductIdsOrderByRatingAsc(Pageable pageable);

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "ORDER BY COALESCE(rs.review_count, 0) DESC, p.id ASC",
           countQuery = "SELECT COUNT(*) FROM product WHERE is_active = true",
           nativeQuery = true)
    Page<Long> findActiveProductIdsOrderByReviewCountDesc(Pageable pageable);

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "ORDER BY COALESCE(rs.review_count, 0) ASC, p.id ASC",
           countQuery = "SELECT COUNT(*) FROM product WHERE is_active = true",
           nativeQuery = true)
    Page<Long> findActiveProductIdsOrderByReviewCountAsc(Pageable pageable);

    /**
     * Verilen ID listesine göre ürünleri tag + parent ile getir (sıra korunmaz, service'de sıralanır).
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.tag t LEFT JOIN FETCH t.parent WHERE p.id IN :ids AND p.isActive = true")
    List<Product> findByIdInWithTagAndParent(@Param("ids") List<Long> ids);

    /**
     * Tag'e ait product'ları tag ve tag.parent bilgileri ile birlikte getir
     * N+1 query problemini önlemek için fetch join kullanılır
     * ProductMapper'da tag.parent'a erişirken lazy loading hatası olmaz
     */
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.tag t " +
           "LEFT JOIN FETCH t.parent " +
           "WHERE p.tag.id = :tagId AND p.isActive = true")
    List<Product> findByTagIdWithTagAndParent(@Param("tagId") Long tagId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.isActive = false")
    int softDeleteAll();

    /**
     * Search & Filter: Sayfa için sadece ID listesi (sayfa kayması önlenir). Sıra: createdAt DESC.
     */
    @Query(value = "SELECT p.id FROM Product p LEFT JOIN p.tag t " +
           "WHERE p.isActive = true " +
           "AND (:q IS NULL OR :q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tagIds IS NULL OR CAST(t.id AS long) IN :tagIds) " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.categoryPath) LIKE LOWER(CONCAT(:categoryPathPrefix, '%'))) " +
           "ORDER BY p.createdAt DESC, p.id ASC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p LEFT JOIN p.tag t " +
           "WHERE p.isActive = true " +
           "AND (:q IS NULL OR :q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tagIds IS NULL OR CAST(t.id AS long) IN :tagIds) " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.categoryPath) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))")
    Page<Long> searchProductIds(
            @Param("q") String q,
            @Param("tagIds") List<Long> tagIds,
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%'))) " +
           "ORDER BY COALESCE(rs.avg_rating, 0) DESC, p.id ASC",
           countQuery = "SELECT COUNT(DISTINCT p.id) FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))",
           nativeQuery = true)
    Page<Long> searchProductIdsByRatingDesc(
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%'))) " +
           "ORDER BY COALESCE(rs.avg_rating, 0) ASC, p.id ASC",
           countQuery = "SELECT COUNT(DISTINCT p.id) FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))",
           nativeQuery = true)
    Page<Long> searchProductIdsByRatingAsc(
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%'))) " +
           "ORDER BY COALESCE(rs.review_count, 0) DESC, p.id ASC",
           countQuery = "SELECT COUNT(DISTINCT p.id) FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))",
           nativeQuery = true)
    Page<Long> searchProductIdsByReviewCountDesc(
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );

    @Query(value = "SELECT p.id FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "LEFT JOIN (" +
           "  SELECT r.product_id AS product_id, AVG(r.rating) AS avg_rating, COUNT(*) AS review_count " +
           "  FROM review r " +
           "  WHERE r.is_active = true " +
           "  GROUP BY r.product_id" +
           ") rs ON rs.product_id = p.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%'))) " +
           "ORDER BY COALESCE(rs.review_count, 0) ASC, p.id ASC",
           countQuery = "SELECT COUNT(DISTINCT p.id) FROM product p " +
           "LEFT JOIN tag t ON p.tag_id = t.id " +
           "WHERE p.is_active = true " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.category_path) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))",
           nativeQuery = true)
    Page<Long> searchProductIdsByReviewCountAsc(
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );

    /**
     * Search & Filter: Eski tek sorgu (geriye dönük uyumluluk için tutuldu; sayfa kayması riski var).
     */
    @Query(value = "SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.tag t " +
           "LEFT JOIN FETCH t.parent " +
           "WHERE p.isActive = true " +
           "AND (:q IS NULL OR :q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tagIds IS NULL OR CAST(t.id AS long) IN :tagIds) " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.categoryPath) LIKE LOWER(CONCAT(:categoryPathPrefix, '%'))) " +
           "ORDER BY p.createdAt DESC, p.id ASC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p LEFT JOIN p.tag t " +
           "WHERE p.isActive = true " +
           "AND (:q IS NULL OR :q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tagIds IS NULL OR CAST(t.id AS long) IN :tagIds) " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.categoryPath) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))")
    Page<Product> searchAndFilter(
            @Param("q") String q,
            @Param("tagIds") List<Long> tagIds,
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );

    /** Admin: Tüm ürünleri (aktif + pasif) tag ile sayfalı getirir */
    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.tag t LEFT JOIN FETCH t.parent ORDER BY p.id",
           countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAllWithTag(Pageable pageable);

    /** Admin: Sadece aktif ürünleri tag ile sayfalı getirir */
    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.tag t LEFT JOIN FETCH t.parent WHERE p.isActive = true ORDER BY p.id",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    Page<Product> findActiveWithTag(Pageable pageable);

    /** Admin: ID ile ürün getirir (aktif/pasif fark etmez), tag ile */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.tag t LEFT JOIN FETCH t.parent WHERE p.id = :id")
    Optional<Product> findByIdWithTagForAdmin(@Param("id") Long id);

    /**
     * Admin: Belirli bir kullanıcının flaglediği review'lere ait ürünleri (aktif+pasif) sayfalı getirir.
     */
    @Query(value = "SELECT DISTINCT p FROM ReviewFlag rf " +
            "JOIN rf.review r " +
            "JOIN r.product p " +
            "LEFT JOIN FETCH p.tag t " +
            "LEFT JOIN FETCH t.parent " +
            "WHERE rf.reportedBy.id = :userId AND rf.isActive = true " +
            "ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(DISTINCT p) FROM ReviewFlag rf " +
                    "JOIN rf.review r " +
                    "JOIN r.product p " +
                    "WHERE rf.reportedBy.id = :userId AND rf.isActive = true")
    Page<Product> findFlaggedProductsByReporterId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Admin: Belirli bir kullanıcının flaglediği review'lere ait sadece aktif ürünleri sayfalı getirir.
     */
    @Query(value = "SELECT DISTINCT p FROM ReviewFlag rf " +
            "JOIN rf.review r " +
            "JOIN r.product p " +
            "LEFT JOIN FETCH p.tag t " +
            "LEFT JOIN FETCH t.parent " +
            "WHERE rf.reportedBy.id = :userId AND rf.isActive = true AND p.isActive = true " +
            "ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(DISTINCT p) FROM ReviewFlag rf " +
                    "JOIN rf.review r " +
                    "JOIN r.product p " +
                    "WHERE rf.reportedBy.id = :userId AND rf.isActive = true AND p.isActive = true")
    Page<Product> findActiveFlaggedProductsByReporterId(@Param("userId") Long userId, Pageable pageable);

    boolean existsByTag_IdAndNameAndIsActiveTrue(Long tagId, String name);
}

