package com.favo.backend.Domain.product.Repository;

import com.favo.backend.Domain.product.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByCategoryPath(String categoryPath);
    
    List<Tag> findByParentId(Long parentId);
    
    List<Tag> findByParentIsNull(); // Root tag'ler (parent'ı olmayanlar)
    
    List<Tag> findByParentIsNullAndIsActiveTrue(); // Aktif root tag'ler
}

