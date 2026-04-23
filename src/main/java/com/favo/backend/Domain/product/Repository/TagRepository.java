package com.favo.backend.Domain.product.Repository;

import com.favo.backend.Domain.product.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByCategoryPath(String categoryPath);
    Optional<Tag> findByCategoryPathAndIsActiveTrue(String categoryPath);
    Optional<Tag> findByIdAndIsActiveTrue(Long id);
    
    List<Tag> findByParentId(Long parentId);
    List<Tag> findByParentIdAndIsActiveTrue(Long parentId);
    
    List<Tag> findByParentIsNull(); // Root tag'ler (parent'ı olmayanlar)
    
    List<Tag> findByParentIsNullAndIsActiveTrue(); // Aktif root tag'ler
    
    // Child'ı olmayan tag'leri bul (leaf node'lar - sadece bunlara product bağlanabilir)
    @Query("SELECT t FROM Tag t WHERE t.isActive = true AND NOT EXISTS (SELECT 1 FROM Tag c WHERE c.parent = t AND c.isActive = true)")
    List<Tag> findLeafTags(); // Child'ı olmayan aktif tag'ler
    
    /**
     * Tag'i aktif child'ları ile birlikte getir (N+1 query problemini önlemek için)
     * LEFT JOIN FETCH ile child'lar tek query'de çekilir
     * DISTINCT kullanarak duplicate sonuçları önler
     */
    @Query("SELECT DISTINCT t FROM Tag t LEFT JOIN FETCH t.children c WHERE t.id = :tagId AND t.isActive = true AND (c.isActive = true OR c IS NULL)")
    Optional<Tag> findByIdWithActiveChildren(@Param("tagId") Long tagId);

    /** Feed kişiselleştirme: leaf tag'lerin parent'ını tek sorguda yükle (N+1 önleme). */
    @Query("SELECT DISTINCT t FROM Tag t LEFT JOIN FETCH t.parent WHERE t.id IN :ids")
    List<Tag> fetchTagsWithParentByIds(@Param("ids") Collection<Long> ids);

    /**
     * Tag ismine göre arama yapar (case-insensitive, LIKE query)
     * Hem name hem de categoryPath'te arama yapar
     * Sadece aktif tag'leri döner
     */
    @Query("SELECT t FROM Tag t WHERE t.isActive = true AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(t.categoryPath) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Tag> searchTagsByName(@Param("searchTerm") String searchTerm);

    /** Admin: Tüm tag'leri sayfalı getirir (id artan) */
    Page<Tag> findAllByOrderByIdAsc(Pageable pageable);

    /** Admin: Sadece aktif tag'leri sayfalı getirir */
    Page<Tag> findByIsActiveTrueOrderByIdAsc(Pageable pageable);

    /**
     * Aktif tüm tag'ler (parent ile) — chat / prompt için kategori ağacı metni üretmek için.
     */
    @Query("SELECT DISTINCT t FROM Tag t LEFT JOIN FETCH t.parent WHERE t.isActive = true ORDER BY t.categoryPath")
    List<Tag> findAllActiveWithParentOrderByCategoryPath();

    /** Verilen path ile başlayan tüm aktif tag id'leri (alt ağaç; ürünler leaf tag'lere bağlı). */
    @Query("SELECT t.id FROM Tag t WHERE t.isActive = true AND LOWER(t.categoryPath) LIKE LOWER(CONCAT(:pathPrefix, '%'))")
    List<Long> findActiveTagIdsUnderPathPrefix(@Param("pathPrefix") String pathPrefix);
}

