package com.favo.backend.Domain.product.Repository;

import com.favo.backend.Domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByIsActiveTrue();
    List<Product> findByTagIdAndIsActiveTrue(Long tagId);
    Optional<Product> findByIdAndIsActiveTrue(Long id);
}

