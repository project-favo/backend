package com.favo.backend.Domain.interaction;

import com.favo.backend.Domain.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_interaction")
@Getter
@Setter
public class ProductInteraction extends Interaction {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_interaction_product")
    )
    private Product targetProduct; // Hangi product'a interaction yapıldı

    @Column(name = "type", nullable = false, length = 50)
    private String type; // Örn: "LIKE", "FAVORITE", "SHARE" vb.

    @Override
    public void recordInteraction() {
        // Interaction kaydı yapılacak işlemler
        // Örn: loglama, istatistik güncelleme vb.
    }
}
