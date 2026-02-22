package com.favo.backend.Service.Product;

import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Tag;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rainforest API JSON dosyasından tag ve ürün import eder.
 * Önce mevcut tüm tag, ürün ve review'ları soft delete yapar, sonra JSON'dan yeni verileri ekler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RainforestImportService {

    private final TagRepository tagRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tüm import işlemini çalıştırır:
     * 1. Mevcut review, product ve tag'lere soft delete
     * 2. JSON'dan tag'leri ekle
     * 3. JSON'dan product'ları ekle
     *
     * @param jsonPath JSON dosya yolu (örn: rainforest_products_output.json)
     */
    @Transactional
    public void runImport(String jsonPath) {
        log.info("=== Rainforest Import başlatılıyor ===");

        // 1. Soft delete
        softDeleteAll();

        // 2. Import tags & products
        importFromJson(jsonPath);

        log.info("=== Rainforest Import tamamlandı ===");
    }

    /**
     * Mevcut tüm review, product ve tag'lere soft delete uygular.
     * Native bulk UPDATE - 3 sorgu ile bitirir, bağlantı timeout riskini azaltır.
     */
    public void softDeleteAll() {
        log.info("Mevcut verilere soft delete uygulanıyor...");

        int r = reviewRepository.softDeleteAll();
        log.info("{} review soft delete edildi", r);

        int p = productRepository.softDeleteAll();
        log.info("{} product soft delete edildi", p);

        int t = tagRepository.softDeleteAll();
        log.info("{} tag soft delete edildi", t);
    }

    /**
     * JSON dosyasından tag ve product'ları import eder.
     */
    public void importFromJson(String jsonPath) {
        Path path = Path.of(jsonPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("JSON dosyası bulunamadı: " + jsonPath);
        }

        try {
            String content = Files.readString(path);
            JsonNode root = objectMapper.readTree(content);

            JsonNode tagStructure = root.get("tagStructure");
            JsonNode tagsArray = tagStructure != null ? tagStructure.get("tags") : null;
            JsonNode productsArray = root.get("convertedProducts");

            if (tagsArray == null || !tagsArray.isArray()) {
                throw new IllegalStateException("JSON'da tagStructure.tags bulunamadı");
            }

            // Tüm mevcut tag'leri tek seferde yükle (N query yerine 1)
            Map<String, Tag> existingByPath = tagRepository.findAll().stream()
                    .collect(Collectors.toMap(Tag::getCategoryPath, tag -> tag, (a, b) -> a));

            // categoryPath -> yeni oluşturulan Tag
            Map<String, Tag> categoryPathToTag = new HashMap<>();

            // Tag'leri sırayla oluştur (parent önce, child sonra - JSON sırası uygun)
            for (JsonNode tagNode : tagsArray) {
                String name = tagNode.get("name").asText();
                String categoryPath = tagNode.get("categoryPath").asText();
                JsonNode parentIdNode = tagNode.get("parentId");

                Tag parent = null;
                if (parentIdNode != null && !parentIdNode.isNull()) {
                    int lastDot = categoryPath.lastIndexOf('.');
                    String parentPath = lastDot > 0 ? categoryPath.substring(0, lastDot) : null;
                    parent = parentPath != null ? categoryPathToTag.get(parentPath) : null;
                }

                Tag existing = existingByPath.get(categoryPath);
                Tag tag;
                if (existing != null) {
                    existing.setIsActive(true);
                    existing.setParent(parent);
                    tag = tagRepository.save(existing);
                } else {
                    tag = new Tag();
                    tag.setName(name);
                    tag.setCategoryPath(categoryPath);
                    tag.setParent(parent);
                    tag.setCreatedAt(LocalDateTime.now());
                    tag.setIsActive(true);
                    tag = tagRepository.save(tag);
                }
                categoryPathToTag.put(categoryPath, tag);
            }
            log.info("{} tag import edildi", categoryPathToTag.size());

            if (productsArray == null || !productsArray.isArray()) {
                log.warn("convertedProducts bulunamadı, ürün import edilmedi");
                return;
            }

            List<Product> productsToSave = new ArrayList<>();
            for (JsonNode prodNode : productsArray) {
                String name = prodNode.get("name").asText();
                String description = prodNode.path("description").asText("");
                String imageURL = prodNode.path("imageURL").asText(null);
                JsonNode tagNode = prodNode.get("tag");
                if (tagNode == null) continue;

                String tagPath = tagNode.path("categoryPath").asText();
                Tag tag = categoryPathToTag.get(tagPath);
                if (tag == null) {
                    log.warn("Tag bulunamadı: {}, ürün atlanıyor: {}", tagPath, name);
                    continue;
                }

                Product product = new Product();
                product.setName(name);
                product.setDescription(description);
                product.setImageURL(imageURL);
                product.setTag(tag);
                product.setCreatedAt(LocalDateTime.now());
                product.setIsActive(true);
                productsToSave.add(product);
            }
            if (!productsToSave.isEmpty()) {
                productRepository.saveAll(productsToSave);
            }
            log.info("{} product import edildi", productsToSave.size());

        } catch (IOException e) {
            throw new RuntimeException("JSON okuma hatası: " + e.getMessage(), e);
        }
    }
}
