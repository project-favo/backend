package com.favo.backend.Domain.product;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageURL;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tag_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_tag")
    )
    private Tag tag;

    // Cascade.ALL kaldırıldı - soft delete için manuel kontrol yapılacak
    // Sadece PERSIST ve MERGE kullanıyoruz (DELETE yok - fiziksel silme yapılmaz)
    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>(); // Bir product'un birden fazla review'ı olabilir

    // Product'a yapılan interaction'lar (0..*)
    @OneToMany(mappedBy = "targetProduct", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<ProductInteraction> interactions = new ArrayList<>();
}
