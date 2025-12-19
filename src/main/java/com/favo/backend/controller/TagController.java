package com.favo.backend.controller;

import com.favo.backend.Domain.product.TagDto;
import com.favo.backend.Service.Product.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * Yeni tag oluştur
     * POST /api/tags
     * Body: { "name": "Iphone13", "parentId": 5 } veya { "name": "Electronics" } (root tag için)
     */
    @PostMapping
    public ResponseEntity<TagDto> createTag(@RequestBody TagCreateRequest request) {
        TagDto created = tagService.createTag(request.getName(), request.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Tüm tag'leri tree yapısında getir (sadece root'lar, children recursive)
     * GET /api/tags/tree
     */
    @GetMapping("/tree")
    public ResponseEntity<List<TagDto>> getTagTree() {
        List<TagDto> tree = tagService.getTagTree();
        return ResponseEntity.ok(tree);
    }

    /**
     * Belirli bir parent'ın child'larını getir
     * GET /api/tags/parent/{parentId}/children
     */
    @GetMapping("/parent/{parentId}/children")
    public ResponseEntity<List<TagDto>> getChildrenByParent(@PathVariable Long parentId) {
        List<TagDto> children = tagService.getChildrenByParentId(parentId);
        return ResponseEntity.ok(children);
    }

    /**
     * Category path'e göre tag getir (tree ile birlikte)
     * GET /api/tags/path?categoryPath=Electronic.Telephone.MobilePhone
     */
    @GetMapping("/path")
    public ResponseEntity<TagDto> getTagByPath(@RequestParam String categoryPath) {
        TagDto tag = tagService.getTagByPath(categoryPath);
        return ResponseEntity.ok(tag);
    }

    /**
     * Tüm tag'leri flat list olarak getir
     * GET /api/tags
     */
    @GetMapping
    public ResponseEntity<List<TagDto>> getAllTags() {
        List<TagDto> tags = tagService.getAllTags();
        return ResponseEntity.ok(tags);
    }

    /**
     * ID'ye göre tag getir (tree ile birlikte)
     * GET /api/tags/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TagDto> getTagById(@PathVariable Long id) {
        TagDto tag = tagService.getTagById(id);
        return ResponseEntity.ok(tag);
    }

    /**
     * Trendyol API'sinden kategorileri import et
     * POST /api/tags/import/trendyol
     * Trendyol API'sinden tüm kategorileri çekip Tag'lere dönüştürür
     */
    @PostMapping("/import/trendyol")
    public ResponseEntity<ImportResponse> importTrendyolCategories() {
        int importedCount = tagService.importTrendyolCategories();
        return ResponseEntity.ok(new ImportResponse(importedCount, "Successfully imported " + importedCount + " categories from Trendyol"));
    }

    /**
     * Import response DTO
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class ImportResponse {
        private int importedCount;
        private String message;
    }

    /**
     * Tag oluşturma için request DTO
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class TagCreateRequest {
        private String name; // Tag adı (örn: "Iphone13")
        private Long parentId; // Parent tag ID (null ise root tag)
    }
}

