package com.favo.backend.controller;

import com.favo.backend.Domain.product.TagDto;
import com.favo.backend.Domain.product.TagWithProductsDto;
import com.favo.backend.Service.Product.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * RBAC: Sadece ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
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
     * 🌳 Root tag'leri getir (parent'ı olmayan tag'ler)
     * GET /api/tags/roots
     * 
     * Frontend'de ilk adım için kullanılır - sadece root tag'ler döner
     * Children olmadan, sadece tag bilgisi (id, name, categoryPath, parentId)
     * Bu sayede frontend'de data yığılması önlenir
     * 
     * Örnek kullanım:
     * 1. Kullanıcı kategori sayfasına girer
     * 2. Bu endpoint çağrılır → sadece root tag'ler gelir (örn: "Electronics", "Books")
     * 3. Kullanıcı bir tag'e tıklar → /api/tags/{id}/children endpoint'i çağrılır
     * 
     * Response: 200 OK + List<TagDto> (children olmadan)
     */
    @GetMapping("/roots")
    public ResponseEntity<List<TagDto>> getRootTags() {
        List<TagDto> rootTags = tagService.getRootTags();
        return ResponseEntity.ok(rootTags);
    }

    /**
     * 🔍 Belirli bir tag'in child'larını getir + eğer leaf tag ise product'ları da döndür
     * GET /api/tags/{id}/children
     * 
     * Frontend'de adım adım tag navigation için kullanılır
     * 
     * Mantık:
     * - Tag'in child'ı varsa → sadece child tag'leri döner (products boş)
     * - Tag'in child'ı yoksa (leaf tag) → o tag'e ait product'ları döner (children boş)
     * 
     * Response formatı:
     * {
     *   "id": 123,
     *   "name": "Iphone13",
     *   "categoryPath": "Electronic.Telephone.MobilePhone.Iphone.Iphone13",
     *   "parentId": 122,
     *   "children": [],  // Eğer leaf tag ise boş
     *   "products": [   // Eğer leaf tag ise dolu, değilse boş
     *     { "id": 1, "name": "iPhone 13 Pro", ... },
     *     { "id": 2, "name": "iPhone 13", ... }
     *   ],
     *   "isLeaf": true  // Bu tag leaf mi?
     * }
     * 
     * Frontend akışı:
     * 1. GET /api/tags/roots → Root tag'leri göster
     * 2. Kullanıcı "Electronics" tag'ine tıklar → GET /api/tags/1/children
     * 3. Eğer child varsa → child tag'leri göster (örn: "Telephone", "Computers")
     * 4. Kullanıcı "Telephone" → "MobilePhone" → "Iphone" → "Iphone13" derinliğine gider
     * 5. "Iphone13" leaf tag ise → products dolu gelir, product listesi gösterilir
     * 
     * Response: 200 OK + TagWithProductsDto
     * Error: 404 Not Found - Tag bulunamazsa
     */
    @GetMapping("/{id}/children")
    public ResponseEntity<TagWithProductsDto> getTagChildrenWithProducts(@PathVariable Long id) {
        TagWithProductsDto result = tagService.getTagChildrenWithProducts(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Belirli bir parent'ın child'larını getir (eski endpoint - backward compatibility için)
     * GET /api/tags/parent/{parentId}/children
     * 
     * ⚠️ YENİ ENDPOINT KULLAN: GET /api/tags/{id}/children (yukarıdaki)
     * Bu endpoint sadece child tag'leri döner, product bilgisi yok
     */
    @GetMapping("/parent/{parentId}/children")
    public ResponseEntity<List<TagDto>> getChildrenByParent(@PathVariable Long parentId) {
        List<TagDto> children = tagService.getChildrenByParentId(parentId);
        return ResponseEntity.ok(children);
    }

    /**
     * 🔍 Tag ismine göre arama yapar (authentication gerektirmez)
     * GET /api/tags/search?name=iPhone
     * 
     * Tag ismi veya categoryPath'te arama yapar (case-insensitive)
     * Sadece aktif tag'leri döner
     * 
     * Örnek kullanım:
     * - GET /api/tags/search?name=iPhone → "iPhone" içeren tüm tag'leri bulur
     * - GET /api/tags/search?name=Telefon → "Telefon" içeren tüm tag'leri bulur
     * - GET /api/tags/search?name=Elektronik → "Elektronik" içeren tüm tag'leri bulur
     * 
     * Response: 200 OK + List<TagDto>
     * Her TagDto içinde: id, name, categoryPath, parentId (children yok, sadece temel bilgiler)
     */
    @GetMapping("/search")
    public ResponseEntity<List<TagDto>> searchTags(@RequestParam(required = false, defaultValue = "") String name) {
        List<TagDto> tags = tagService.searchTagsByName(name);
        return ResponseEntity.ok(tags);
    }

    /**
     * Category path'e göre tag getir (tree ile birlikte)
     * GET /api/tags/path?categoryPath=Electronic.Telephone.MobilePhone
     * 
     * Tag bulunamazsa 404 döndürür (script import için önemli)
     */
    @GetMapping("/path")
    public ResponseEntity<TagDto> getTagByPath(@RequestParam String categoryPath) {
        try {
            TagDto tag = tagService.getTagByPath(categoryPath);
            return ResponseEntity.ok(tag);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Tag not found")) {
                return ResponseEntity.notFound().build();
            }
            throw e; // Diğer hatalar için exception'ı fırlat
        }
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
     * Trendyol API'sinden kategori import endpoint'i PASİF.
     * Artık Trendyol'dan tag import edilmiyor, bu endpoint 410 GONE döner.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import/trendyol")
    public ResponseEntity<ImportResponse> importTrendyolCategories() {
        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(new ImportResponse(0, "Trendyol import endpoint is disabled. Tags are no longer imported from Trendyol."));
    }

    /**
     * Tag'i soft delete yap (isActive = false)
     * DELETE /api/tags/{id}
     * RBAC: Sadece ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
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

