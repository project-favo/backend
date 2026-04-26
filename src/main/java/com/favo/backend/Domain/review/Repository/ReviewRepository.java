package com.favo.backend.Domain.review.Repository;

import com.favo.backend.Domain.review.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdAndIsActiveTrue(Long productId);

    List<Review> findByProduct_IdAndIsActiveTrueOrderByCreatedAtDesc(Long productId, Pageable pageable);
    List<Review> findByOwnerIdAndIsActiveTrue(Long ownerId);

    /** Recent reviews by this user (newest first), for personalization context */
    List<Review> findByOwnerIdAndIsActiveTrueOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
    Optional<Review> findByIdAndIsActiveTrue(Long id);
    List<Review> findByIsActiveTrue();

    /**
     * ID'ye göre review'ı product ve owner ile getirir.
     * <p><b>mediaList JOIN FETCH yok</b> — her {@code media.image_data} (LONGBLOB) aynı sorguda çekilse
     * bellek / paket sınırı ve 500 riski yaratıyor; medya ayrı lazy (veya BLOB=LAZY) yüklenir.
     * Interactions fetch edilmez.
     */
    @Query("SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "WHERE r.id = :id AND r.isActive = true AND p.isActive = true AND o.isActive = true")
    Optional<Review> findByIdWithRelations(@Param("id") Long id);

    /**
     * Product'a ait aktif review'lar — product, owner yüklenir; medya <b>fetch join edilmez</b> (BLOB boyutu / cartesian).
     */
    @Query("SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "WHERE r.product.id = :productId AND r.isActive = true AND p.isActive = true AND o.isActive = true")
    List<Review> findByProductIdWithRelations(@Param("productId") Long productId);

    /**
     * Kullanıcıya ait review'lar (en yeni önce). Medya fetch join yok; gerekçe
     * {@link #findByProductIdWithRelations(Long)} ile aynı.
     */
    @Query("SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "WHERE r.owner.id = :ownerId AND r.isActive = true AND p.isActive = true AND o.isActive = true ORDER BY r.createdAt DESC")
    List<Review> findByOwnerIdWithRelations(@Param("ownerId") Long ownerId);

    /**
     * Giriş yapan kullanıcının review'ları — sayfalı (My Reviews). Medya fetch join yok.
     */
    @Query(value = "SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "WHERE r.owner.id = :ownerId AND r.isActive = true AND p.isActive = true AND o.isActive = true",
           countQuery = "SELECT COUNT(DISTINCT r) FROM Review r " +
           "WHERE r.owner.id = :ownerId AND r.isActive = true AND r.product.isActive = true AND r.owner.isActive = true")
    Page<Review> findByOwnerIdWithRelationsPage(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "WHERE r.owner.id IN :ownerIds AND r.isActive = true AND p.isActive = true " +
           "ORDER BY r.createdAt DESC")
    List<Review> findRecentFeedReviewsByOwnerIds(@Param("ownerIds") List<Long> ownerIds, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.owner.id IN :ownerIds AND r.isActive = true AND r.product.isActive = true")
    long countActiveByOwnerIds(@Param("ownerIds") List<Long> ownerIds);

    /**
     * Product'a ait aktif review'ların ortalama rating'ini hesapla
     * Review yoksa null döner
     */
    @Query("SELECT AVG(r.rating) FROM Review r " +
           "WHERE r.product.id = :productId AND r.isActive = true")
    Double calculateAverageRatingByProductId(@Param("productId") Long productId);

    /**
     * Product'a ait aktif review sayısını hesapla
     */
    @Query("SELECT COUNT(r) FROM Review r " +
           "WHERE r.product.id = :productId AND r.isActive = true")
    Long countReviewsByProductId(@Param("productId") Long productId);

    /**
     * Giriş yapan kullanıcının tüm vitrin-uyumlu aktif yorumlarının ortalama rating'i.
     * [getMyReviews] sayfalı sorgu ile aynı filtre (owner + aktif product + aktif owner).
     */
    @Query("SELECT AVG(r.rating) FROM Review r " +
           "WHERE r.owner.id = :ownerId AND r.isActive = true " +
           "AND r.product.isActive = true AND r.owner.isActive = true")
    Double calculateAverageRatingByOwnerId(@Param("ownerId") Long ownerId);

    /** Admin: Tüm review'ları (aktif + pasif) product ve owner ile sayfalı getirir */
    @Query(value = "SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.product p LEFT JOIN FETCH r.owner o ORDER BY r.id",
           countQuery = "SELECT COUNT(r) FROM Review r")
    Page<Review> findAllWithRelations(Pageable pageable);

    /** Admin: Sadece aktif review'ları product ve owner ile sayfalı getirir */
    @Query(value = "SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.product p LEFT JOIN FETCH r.owner o WHERE r.isActive = true ORDER BY r.id",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.isActive = true")
    Page<Review> findActiveWithRelations(Pageable pageable);

    /** Admin: ID ile review getirir (aktif/pasif fark etmez), product ve owner ile (media lazy) */
    @Query("SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.product p LEFT JOIN FETCH r.owner o WHERE r.id = :id")
    Optional<Review> findByIdWithRelationsForAdmin(@Param("id") Long id);

    /** Kullanıcının bu ürüne aktif yorumu var mı? */
    boolean existsByOwnerIdAndProductIdAndIsActiveTrue(Long ownerId, Long productId);

    /** Aynı üründe yorumu olan diğer kullanıcıların id'leri (yeni yorum yazanı hariç). */
    @Query("SELECT DISTINCT r.owner.id FROM Review r WHERE r.product.id = :productId AND r.isActive = true AND r.owner.id <> :excludeOwnerId")
    List<Long> findDistinctOwnerIdsByProductIdExcludingOwner(
            @Param("productId") Long productId,
            @Param("excludeOwnerId") Long excludeOwnerId
    );

    @Query("SELECT r.owner.id AS userId, r.owner.userName AS userName, COUNT(r) AS reviewCount " +
           "FROM Review r " +
           "WHERE r.isActive = true AND r.owner.isActive = true " +
           "GROUP BY r.owner.id, r.owner.userName " +
           "ORDER BY COUNT(r) DESC, r.owner.id ASC")
    List<TopReviewerProjection> findTopReviewers(Pageable pageable);
}

