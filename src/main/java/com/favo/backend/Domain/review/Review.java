package com.favo.backend.Domain.review;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.interaction.ReviewInteraction;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review")
@Getter
@Setter
public class Review extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_collaborative")
    private Boolean isCollaborative = false;

    @Column(name = "rating", nullable = false)
    private Integer rating; // 1-5 arası rating

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_product")
    )
    private Product product; // Review hangi product için

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_owner")
    )
    private SystemUser owner; // Review'ı yazan kullanıcı

    // Review'ın media dosyaları (0..*); BLOB'lar ayrı yüklensin diye batch
    @OneToMany(mappedBy = "review", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    private List<Media> mediaList = new ArrayList<>();

    // Review'a yapılan interaction'lar (0..*)
    @OneToMany(mappedBy = "targetReview", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<ReviewInteraction> interactions = new ArrayList<>();

    @Column(name = "toxicity_score")
    private Double toxicityScore;

    @Column(name = "toxicity_checked_at")
    private LocalDateTime toxicityCheckedAt;

    @Column(name = "auto_flagged", nullable = false)
    private Boolean autoFlagged = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;
}
