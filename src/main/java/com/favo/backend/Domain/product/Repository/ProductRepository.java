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
     * Ana sayfa: Aktif ürünleri oluşturulma tarihine göre (en yeni önce) sayfalı getir.
     * Tag + parent fetch ile N+1 önlenir. Sayfa başına 20 ürün.
     */
    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.tag t LEFT JOIN FETCH t.parent WHERE p.isActive = true ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    Page<Product> findActiveProductsOrderByCreatedAtDesc(Pageable pageable);

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
     * Search & Filter: metin araması (name, description), tag filtresi, category path prefix.
     * Parametreler boş/null ise o koşul uygulanmaz.
     * countQuery fetch join içermez (sayfalama count için gerekli).
     */
    @Query(value = "SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.tag t " +
           "LEFT JOIN FETCH t.parent " +
           "WHERE p.isActive = true " +
           "AND (:q IS NULL OR :q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tagIds IS NULL OR SIZE(:tagIds) = 0 OR p.tag.id IN :tagIds) " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.categoryPath) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p LEFT JOIN p.tag t " +
           "WHERE p.isActive = true " +
           "AND (:q IS NULL OR :q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:tagIds IS NULL OR SIZE(:tagIds) = 0 OR p.tag.id IN :tagIds) " +
           "AND (:categoryPathPrefix IS NULL OR :categoryPathPrefix = '' OR LOWER(t.categoryPath) LIKE LOWER(CONCAT(:categoryPathPrefix, '%')))")
    Page<Product> searchAndFilter(
            @Param("q") String q,
            @Param("tagIds") List<Long> tagIds,
            @Param("categoryPathPrefix") String categoryPathPrefix,
            Pageable pageable
    );
}

