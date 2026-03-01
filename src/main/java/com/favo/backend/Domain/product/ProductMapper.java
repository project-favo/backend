package com.favo.backend.Domain.product;

public class ProductMapper {
    public static ProductResponseDto toDto(Product product) {
        TagDto tagDto = null;
        if (product.getTag() != null) {
            tagDto = new TagDto(
                    product.getTag().getId(),
                    product.getTag().getName(),
                    product.getTag().getCategoryPath(),
                    product.getTag().getParent() != null ? product.getTag().getParent().getId() : null,
                    new java.util.ArrayList<>()
            );
        }

        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageURL(),
                tagDto,
                product.getCreatedAt(),
                product.getIsActive(),
                null
        );
    }

    /** Wishlist için: ürün + beğenilme tarihi */
    public static ProductResponseDto toDto(com.favo.backend.Domain.product.Product product, java.time.LocalDateTime likedAt) {
        ProductResponseDto dto = toDto(product);
        return new ProductResponseDto(
                dto.getId(),
                dto.getName(),
                dto.getDescription(),
                dto.getImageURL(),
                dto.getTag(),
                dto.getCreatedAt(),
                dto.getIsActive(),
                likedAt
        );
    }
}

