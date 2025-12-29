package com.favo.backend.Domain.interaction.Repository;

import com.favo.backend.Domain.interaction.ProductInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductInteractionRepository extends JpaRepository<ProductInteraction, Long> {
    
    /**
     * Belirli bir kullanıcının belirli bir product'a yaptığı belirli tipte interaction'ı bulur
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
     * Belirli bir product'a yapılan belirli tipte interaction sayısını döner
     */
    @Query("SELECT COUNT(pi) FROM ProductInteraction pi " +
           "WHERE pi.targetProduct.id = :productId " +
           "AND pi.type = :type " +
           "AND pi.isActive = true")
    Long countByProductIdAndType(@Param("productId") Long productId, @Param("type") String type);
}

