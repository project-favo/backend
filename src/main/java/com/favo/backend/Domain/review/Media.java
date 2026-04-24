package com.favo.backend.Domain.review;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter
@Setter
public class Media extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "review_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_media_review")
    )
    private Review review; // Media hangi review'a ait

    /**
     * Liste/detay sorgularında tüm BLOB'u ihtiyacı olmadan çekmemek için (JOIN FETCH + çok medya = OOM/500).
     * Gerçek okuma, görsel uç noktaları ve indirim senaryolarında tetiklenir.
     */
    @Lob
    @Basic(fetch = FetchType.LAZY)
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

    public byte[] retrieveFile() {
        return imageData;
    }
}
