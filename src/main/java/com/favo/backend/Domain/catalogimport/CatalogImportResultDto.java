package com.favo.backend.Domain.catalogimport;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CatalogImportResultDto {
    private final int tagsCreated;
    private final int tagsReusedExisting;
    private final int productsCreated;
    private final int productsSkippedDuplicate;
}
