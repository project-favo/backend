package com.favo.backend.Domain.product;

import com.favo.backend.Domain.Common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tag")
@Getter
@Setter
public class Tag extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name; // Örnek: "Iphone13", "MobilePhone"

    @Column(name = "category_path", nullable = false, length = 500, unique = true)
    private String categoryPath; // Otomatik oluşturulur: "Electronic.Telephone.MobilePhone.Iphone.Iphone13"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            foreignKey = @ForeignKey(name = "fk_tag_parent")
    )
    private Tag parent; // Parent tag (null ise root tag)

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Tag> children = new ArrayList<>(); // Child tag'ler
}