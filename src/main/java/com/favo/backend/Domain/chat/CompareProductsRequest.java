package com.favo.backend.Domain.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompareProductsRequest {

    @NotNull
    private Long productId1;

    @NotNull
    private Long productId2;
}
