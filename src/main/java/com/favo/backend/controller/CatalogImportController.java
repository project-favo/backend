package com.favo.backend.controller;

import com.favo.backend.Domain.catalogimport.CatalogImportResultDto;
import com.favo.backend.Service.Product.CatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Geçici: docs/products.json ile aynı içeriği classpath'ten (import/products.json) DB'ye yazar.
 * Auth yok; prod'da {@code app.catalog-import.enabled=false} bırakın.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class CatalogImportController {

    private final CatalogImportService catalogImportService;

    @Value("${app.catalog-import.enabled:false}")
    private boolean catalogImportEnabled;

    @PostMapping("/catalog-import-from-json")
    public ResponseEntity<?> importCatalog() {
        if (!catalogImportEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "CATALOG_IMPORT_DISABLED",
                    "message", "Import kapalı. app.catalog-import.enabled=true veya APP_CATALOG_IMPORT_ENABLED=true ile açın (varsayılan yerelde açık; false verdiyseniz kaldırın)."
            ));
        }
        try {
            CatalogImportResultDto result = catalogImportService.importFromClasspath();
            return ResponseEntity.ok(Map.of(
                    "tagsCreated", result.getTagsCreated(),
                    "tagsReusedExisting", result.getTagsReusedExisting(),
                    "productsCreated", result.getProductsCreated(),
                    "productsSkippedDuplicate", result.getProductsSkippedDuplicate()
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CATALOG_IMPORT_FAILED",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Import failed"
            ));
        }
    }
}
