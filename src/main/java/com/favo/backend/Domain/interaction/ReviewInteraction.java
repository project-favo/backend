package com.favo.backend.Domain.interaction;

import com.favo.backend.Domain.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * ReviewInteraction entity
 * ERD'ye uygun olarak Interaction tablosuna JOIN ile bağlanır
 * interactionID -> Interaction.id (primary key join column)
 */
@Entity
@Table(name = "review_interaction")
@PrimaryKeyJoinColumn(name = "interaction_id", referencedColumnName = "id")
@DiscriminatorValue("REVIEW_INTERACTION")
@Getter
@Setter
public class ReviewInteraction extends Interaction {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_review_id",
            nullable = false
            // Foreign key constraint name'i kaldırıldı - Hibernate otomatik benzersiz isim oluşturur
            // Bu, "Duplicate foreign key constraint name" hatasını önler
    )
    private Review targetReview; // Hangi review'a interaction yapıldı

    @Column(name = "type", nullable = false, length = 50)
    private String type; // Örn: "LIKE", "DISLIKE", "REPORT" vb.

    @Override
    public void recordInteraction() {
        // Interaction kaydı yapılacak işlemler
        // Örn: loglama, istatistik güncelleme vb.
    }
}
