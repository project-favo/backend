package com.favo.backend.Domain.product.Repository;

import com.favo.backend.Domain.product.Product;
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
}

