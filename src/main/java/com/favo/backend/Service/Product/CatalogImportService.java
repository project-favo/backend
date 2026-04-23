package com.favo.backend.Service.Product;

import com.favo.backend.Domain.catalogimport.CatalogImportFileDto;
import com.favo.backend.Domain.catalogimport.CatalogImportFileDto.ImportProductRow;
import com.favo.backend.Domain.catalogimport.CatalogImportFileDto.ImportTagRow;
import com.favo.backend.Domain.catalogimport.CatalogImportResultDto;
import com.favo.backend.Domain.product.ProductRequestDto;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogImportService {

    private static final String CLASSPATH_CATALOG = "classpath:import/products.json";
    /** Projede ObjectMapper bean olmayabiliyor; import için yerel instance yeterli. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResourceLoader resourceLoader;
    private final TagService tagService;
    private final ProductService productService;
    private final TagRepository tagRepository;
    private final ProductRepository productRepository;

    /**
     * classpath:import/products.json — önce tag'ler (kökten yaprağa), sonra ürünler.
     * Mevcut categoryPath için tag oluşturulmaz, mevcut id eşlemeye alınır.
     * Aynı tag + isimde aktif ürün varsa ürün atlanır.
     */
    @Transactional
    public CatalogImportResultDto importFromClasspath() {
        CatalogImportFileDto file = readFile();
        List<ImportTagRow> tags = file.getTags();
        List<ImportProductRow> products = file.getProducts();
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("Import file has no tags");
        }
        if (products == null) {
            throw new IllegalArgumentException("Import file has no products array");
        }

        tags.sort(Comparator
                .comparingInt((ImportTagRow t) -> t.getCategoryPath().split("\\.", -1).length)
                .thenComparing(ImportTagRow::getId, Comparator.nullsLast(Long::compareTo)));

        Map<Long, Long> jsonTagIdToDbId = new HashMap<>();
        int tagsCreated = 0;
        int tagsReused = 0;

        for (ImportTagRow row : tags) {
            if (row.getId() == null || row.getName() == null || row.getName().isBlank() || row.getCategoryPath() == null) {
                throw new IllegalArgumentException("Invalid tag row: " + row);
            }
            var existing = tagRepository.findByCategoryPath(row.getCategoryPath());
            if (existing.isPresent()) {
                jsonTagIdToDbId.put(row.getId(), existing.get().getId());
                tagsReused++;
                continue;
            }
            Long dbParentId = null;
            if (row.getParentId() != null) {
                dbParentId = jsonTagIdToDbId.get(row.getParentId());
                if (dbParentId == null) {
                    throw new IllegalStateException(
                            "Parent tag id " + row.getParentId() + " not resolved before child " + row.getCategoryPath());
                }
            }
            var created = tagService.createTag(row.getName().trim(), dbParentId);
            jsonTagIdToDbId.put(row.getId(), created.getId());
            tagsCreated++;
        }

        int productsCreated = 0;
        int productsSkipped = 0;

        for (ImportProductRow p : products) {
            if (p.getName() == null || p.getName().isBlank()) {
                throw new IllegalArgumentException("Product without name");
            }
            if (p.getTag() == null || p.getTag().getId() == null) {
                throw new IllegalArgumentException("Product without tag id: " + p.getName());
            }
            Long dbTagId = jsonTagIdToDbId.get(p.getTag().getId());
            if (dbTagId == null) {
                throw new IllegalStateException("Unknown tag id " + p.getTag().getId() + " for product " + p.getName());
            }
            if (productRepository.existsByTag_IdAndNameAndIsActiveTrue(dbTagId, p.getName())) {
                productsSkipped++;
                continue;
            }
            ProductRequestDto req = new ProductRequestDto();
            req.setName(p.getName().trim());
            req.setDescription(p.getDescription() != null ? p.getDescription() : "");
            req.setImageURL(blankToNull(p.getImageURL()));
            req.setTagId(dbTagId);
            productService.createProduct(req);
            productsCreated++;
        }

        log.info("Catalog import done: tagsCreated={}, tagsReused={}, productsCreated={}, productsSkipped={}",
                tagsCreated, tagsReused, productsCreated, productsSkipped);
        return new CatalogImportResultDto(tagsCreated, tagsReused, productsCreated, productsSkipped);
    }

    private CatalogImportFileDto readFile() {
        Resource resource = resourceLoader.getResource(CLASSPATH_CATALOG);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing resource " + CLASSPATH_CATALOG);
        }
        try (InputStream in = resource.getInputStream()) {
            return JSON.readValue(in, CatalogImportFileDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read catalog JSON: " + e.getMessage(), e);
        }
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
