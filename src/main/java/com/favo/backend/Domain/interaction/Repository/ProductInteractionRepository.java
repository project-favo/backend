package com.favo.backend.Domain.interaction.Repository;

import com.favo.backend.Domain.interaction.ProductInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ProductInteractionRepository extends JpaRepository<ProductInteraction, Long> {
    
    /**
     * Belirli bir kullanıcının belirli bir product'a yaptığı belirli tipte interaction'ı bulur (sadece aktif olanlar)
     */
    @Query("SELECT pi FROM ProductInteraction pi " +
           "WHERE pi.performer.id = :performerId " +
           "AND pi.targetProduct.id = :productId " +
           "AND pi.type = :type " +
           "AND pi.isActive = true")
    Optional<ProductInteraction> findByPerformerIdAndProductIdAndType(
            @Param("performerId") Long performerId,
            @Param("productId") Long productId,
            @Param("type") String type
    );

    /**
     * Belirli bir kullanıcının belirli bir product'a yaptığı belirli tipte interaction'ı bulur (isActive kontrolü yapmadan)
     * Toggle işlemleri için kullanılır - soft delete edilmiş kayıtları da bulur
     */
    @Query("SELECT pi FROM ProductInteraction pi " +
           "WHERE pi.performer.id = :performerId " +
           "AND pi.targetProduct.id = :productId " +
           "AND pi.type = :type")
    Optional<ProductInteraction> findByPerformerIdAndProductIdAndTypeIgnoreActive(
            @Param("performerId") Long performerId,
            @Param("productId") Long productId,
            @Param("type") String type
    );

    /**
     * Belirli bir product'a yapılan belirli tipte interaction sayısını döner
     */
    @Query("SELECT COUNT(pi) FROM ProductInteraction pi " +
           "WHERE pi.targetProduct.id = :productId " +
           "AND pi.type = :type " +
           "AND pi.isActive = true")
    Long countByProductIdAndType(@Param("productId") Long productId, @Param("type") String type);

    /**
     * Kullanıcının belirli bir product'a verdiği rating'i bulur
     */
    @Query("SELECT pi FROM ProductInteraction pi " +
           "WHERE pi.performer.id = :performerId " +
           "AND pi.targetProduct.id = :productId " +
           "AND pi.type = 'RATING' " +
           "AND pi.isActive = true")
    Optional<ProductInteraction> findRatingByPerformerIdAndProductId(
            @Param("performerId") Long performerId,
            @Param("productId") Long productId
    );

    /**
     * Product'ın ortalama rating'ini hesaplar
     */
    @Query("SELECT AVG(pi.rating) FROM ProductInteraction pi " +
           "WHERE pi.targetProduct.id = :productId " +
           "AND pi.type = 'RATING' " +
           "AND pi.isActive = true " +
           "AND pi.rating IS NOT NULL")
    Double calculateAverageRating(@Param("productId") Long productId);

    /**
     * Kullanıcının beğendiği (wishlist) ürünleri sayfalı getirir.
     * Product + tag + tag.parent fetch ile N+1 önlenir. Sadece aktif like ve aktif product'lar.
     */
    @Query(value = "SELECT DISTINCT pi FROM ProductInteraction pi " +
           "LEFT JOIN FETCH pi.targetProduct p " +
           "LEFT JOIN FETCH p.tag t " +
           "LEFT JOIN FETCH t.parent " +
           "WHERE pi.performer.id = :performerId AND pi.type = 'LIKE' AND pi.isActive = true AND p.isActive = true " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInteraction pi " +
           "WHERE pi.performer.id = :performerId AND pi.type = 'LIKE' AND pi.isActive = true AND pi.targetProduct.isActive = true")
    Page<ProductInteraction> findLikedProductsByPerformerId(@Param("performerId") Long performerId, Pageable pageable);

    @Query("SELECT pi FROM ProductInteraction pi " +
           "LEFT JOIN FETCH pi.targetProduct p " +
           "LEFT JOIN FETCH pi.performer u " +
           "WHERE pi.performer.id IN :performerIds AND pi.type = 'LIKE' AND pi.isActive = true AND p.isActive = true " +
           "ORDER BY pi.createdAt DESC")
    List<ProductInteraction> findRecentFeedLikesByPerformerIds(
            @Param("performerIds") List<Long> performerIds,
            Pageable pageable
    );

    @Query("SELECT COUNT(pi) FROM ProductInteraction pi " +
           "WHERE pi.performer.id IN :performerIds AND pi.type = 'LIKE' AND pi.isActive = true AND pi.targetProduct.isActive = true")
    long countActiveLikesByPerformerIds(@Param("performerIds") List<Long> performerIds);

    /**
     * Belirli bir product için aktif REPORT etkileşimlerini sayfalı döner.
     */
    @Query("SELECT pi FROM ProductInteraction pi " +
           "WHERE pi.targetProduct.id = :productId " +
           "AND pi.type = 'REPORT' " +
           "AND pi.isActive = true " +
           "ORDER BY pi.createdAt DESC")
    Page<ProductInteraction> findReportsByProductId(@Param("productId") Long productId, Pageable pageable);
}

