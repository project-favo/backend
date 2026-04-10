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

    /**
     * Ana sayfa: Sayfa için sadece ID listesi (DISTINCT+FETCH sayfa kaymasını önlemek için).
     * Sıra sabit: createdAt DESC, id ASC.
     */
    @Query(value = "SELECT p.id FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC, p.id ASC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    Page<Long> findActiveProductIdsOrderByCreatedAtDescIdAsc(Pageable pageable);

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
     * Search & Filter: Sayfa için sadece ID listesi (sayfa kayması önlenir).
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
}

