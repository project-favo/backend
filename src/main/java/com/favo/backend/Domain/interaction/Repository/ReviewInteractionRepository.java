package com.favo.backend.Domain.interaction.Repository;

import com.favo.backend.Domain.interaction.ReviewInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewInteractionRepository extends JpaRepository<ReviewInteraction, Long> {
    
    /**
     * Belirli bir kullanıcının belirli bir review'a yaptığı belirli tipte interaction'ı bulur (sadece aktif olanlar)
     */
    @Query("SELECT ri FROM ReviewInteraction ri " +
           "WHERE ri.performer.id = :performerId " +
           "AND ri.targetReview.id = :reviewId " +
           "AND ri.type = :type " +
           "AND ri.isActive = true")
    Optional<ReviewInteraction> findByPerformerIdAndReviewIdAndType(
            @Param("performerId") Long performerId,
            @Param("reviewId") Long reviewId,
            @Param("type") String type
    );

    /**
     * Belirli bir kullanıcının belirli bir review'a yaptığı belirli tipte interaction'ı bulur (isActive kontrolü yapmadan)
     * Toggle işlemleri için kullanılır - soft delete edilmiş kayıtları da bulur
     */
    @Query("SELECT ri FROM ReviewInteraction ri " +
           "WHERE ri.performer.id = :performerId " +
           "AND ri.targetReview.id = :reviewId " +
           "AND ri.type = :type")
    Optional<ReviewInteraction> findByPerformerIdAndReviewIdAndTypeIgnoreActive(
            @Param("performerId") Long performerId,
            @Param("reviewId") Long reviewId,
            @Param("type") String type
    );

    /**
     * Belirli bir review'a yapılan belirli tipte interaction sayısını döner
     */
    @Query("SELECT COUNT(ri) FROM ReviewInteraction ri " +
           "WHERE ri.targetReview.id = :reviewId " +
           "AND ri.type = :type " +
           "AND ri.isActive = true")
    Long countByReviewIdAndType(@Param("reviewId") Long reviewId, @Param("type") String type);
}

