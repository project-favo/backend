package com.favo.backend.Domain.review.Repository;

import com.favo.backend.Domain.review.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {
    
    /**
     * ID'ye göre aktif media'yı getirir
     */
    Optional<Media> findByIdAndIsActiveTrue(Long id);
}

