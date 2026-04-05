package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.ProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {

    /**
     * Kullanıcının aktif profil fotoğrafını getirir
     */
    @Query("SELECT pp FROM ProfilePhoto pp " +
           "WHERE pp.user.id = :userId " +
           "AND pp.isActive = true")
    Optional<ProfilePhoto> findActiveByUserId(@Param("userId") Long userId);

    /**
     * Kullanıcının tüm profil fotoğraflarını getirir (aktif ve pasif)
     */
    @Query("SELECT pp FROM ProfilePhoto pp " +
           "WHERE pp.user.id = :userId " +
           "ORDER BY pp.uploadDate DESC")
    java.util.List<ProfilePhoto> findAllByUserId(@Param("userId") Long userId);

}

