package com.favo.backend.Domain.interaction;

import com.favo.backend.Domain.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * ProductInteraction entity
 * ERD'ye uygun olarak Interaction tablosuna JOIN ile bağlanır
 * interactionID -> Interaction.id (primary key join column)
 */
@Entity
@Table(name = "product_interaction")
@PrimaryKeyJoinColumn(name = "interaction_id", referencedColumnName = "id")
@DiscriminatorValue("PRODUCT_INTERACTION")
@Getter
@Setter
public class ProductInteraction extends Interaction {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_product_id",
            nullable = false
            // Foreign key constraint name'i kaldırıldı - Hibernate otomatik benzersiz isim oluşturur
            // Bu, "Duplicate foreign key constraint name" hatasını önler
    )
    private Product targetProduct; // Hangi product'a interaction yapıldı
 
    @Column(name = "type", nullable = false, length = 50)
    private String type; // Örn: "LIKE", "WISHLIST", "RATING" vb.

    @Column(name = "rating")
    private Integer rating; // 1-5 arası rating (sadece type="RATING" olduğunda dolu, diğerlerinde null)

    @Override
    public void recordInteraction() {
        // Interaction kaydı yapılacak işlemler
        // Örn: loglama, istatistik güncelleme vb.
    }
}
