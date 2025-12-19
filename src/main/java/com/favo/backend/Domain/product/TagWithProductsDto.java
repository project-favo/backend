package com.favo.backend.Domain.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag bilgisi + eğer leaf tag ise product'ları içeren DTO
 * Frontend'de adım adım tag navigation için kullanılır
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagWithProductsDto {
    private Long id;
    private String name;
    private String categoryPath;
    private Long parentId;
    private List<TagDto> children = new ArrayList<>(); // Child tag'ler (varsa)
    private List<ProductResponseDto> products = new ArrayList<>(); // Eğer leaf tag ise product'lar
    private Boolean isLeaf; // Bu tag leaf mi? (child'ı yok mu?)
}

