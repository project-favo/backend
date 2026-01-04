package com.favo.backend.Domain.user;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ProfilePhoto Entity
 * Kullanıcı profil fotoğraflarını saklar
 * Her kullanıcının aktif bir profil fotoğrafı olabilir (isActive = true)
 * Eski fotoğraflar soft delete edilir (isActive = false) - geçmiş fotoğraflar saklanır
 */
@Entity
@Table(name = "profile_photo")
@Getter
@Setter
public class ProfilePhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_profile_photo_user")
    )
    private SystemUser user; // Fotoğraf hangi kullanıcıya ait

    @Lob
    @Column(name = "image_data", columnDefinition = "LONGBLOB")
    private byte[] imageData; // Binary image data

    @Column(name = "mime_type", length = 100)
    private String mimeType; // Örn: "image/jpeg", "image/png"

    @Column(name = "upload_date", nullable = false)
    private LocalDateTime uploadDate;

    @PrePersist
    protected void onCreate() {
        if (uploadDate == null) {
            uploadDate = LocalDateTime.now();
        }
    }

    /**
     * Fotoğrafı byte array olarak döner
     */
    public byte[] retrieveFile() {
        return imageData;
    }
}

