package com.favo.backend.Domain.review.Repository;

import com.favo.backend.Domain.review.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdAndIsActiveTrue(Long productId);
    List<Review> findByOwnerIdAndIsActiveTrue(Long ownerId);
    Optional<Review> findByIdAndIsActiveTrue(Long id);
    List<Review> findByIsActiveTrue();

    /**
     * ID'ye göre review'ı tüm ilişkileriyle birlikte getir (N+1 query problemini önlemek için)
     * Not: interactions fetch edilmiyor (MultipleBagFetchException'ı önlemek için)
     */
    @Query("SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "LEFT JOIN FETCH r.mediaList m " +
           "WHERE r.id = :id AND r.isActive = true")
    Optional<Review> findByIdWithRelations(@Param("id") Long id);

    /**
     * Product'a ait review'ları tüm ilişkileriyle birlikte getir
     * Not: interactions fetch edilmiyor (MultipleBagFetchException'ı önlemek için)
     */
    @Query("SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "LEFT JOIN FETCH r.mediaList m " +
           "WHERE r.product.id = :productId AND r.isActive = true")
    List<Review> findByProductIdWithRelations(@Param("productId") Long productId);

    /**
     * Kullanıcıya ait review'ları tüm ilişkileriyle birlikte getir
     * Not: interactions fetch edilmiyor (MultipleBagFetchException'ı önlemek için)
     */
    @Query("SELECT DISTINCT r FROM Review r " +
           "LEFT JOIN FETCH r.product p " +
           "LEFT JOIN FETCH r.owner o " +
           "LEFT JOIN FETCH r.mediaList m " +
           "WHERE r.owner.id = :ownerId AND r.isActive = true")
    List<Review> findByOwnerIdWithRelations(@Param("ownerId") Long ownerId);

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
}

