package com.favo.backend.Service.Product;

import com.favo.backend.Domain.product.*;
import com.favo.backend.Domain.product.External.TrendyolCategory;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Service.Product.External.TrendyolCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TagService {

    private final TagRepository tagRepository;
    private final TrendyolCategoryService trendyolCategoryService;
    private final ProductRepository productRepository;

    /**
     * Yeni tag oluştur (hiyerarşik yapı ile)
     * @param name Tag adı (örn: "Iphone13")
     * @param parentId Parent tag ID (null ise root tag)
     */
    public TagDto createTag(String name, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Tag name is required");
        }

        Tag parent = null;
        String categoryPath = name;

        // Parent varsa path'i oluştur
        if (parentId != null) {
            parent = tagRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent tag not found with id: " + parentId));
            categoryPath = parent.getCategoryPath() + "." + name;
        }

        // Aynı categoryPath varsa hata
        if (tagRepository.findByCategoryPath(categoryPath).isPresent()) {
            throw new RuntimeException("Tag with categoryPath already exists: " + categoryPath);
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setCategoryPath(categoryPath);
        tag.setParent(parent);
        tag.setCreatedAt(LocalDateTime.now());
        tag.setIsActive(true);

        Tag saved = tagRepository.save(tag);
        return toDto(saved);
    }

    /**
     * Tüm tag'leri tree yapısında getir
     */
    public List<TagDto> getTagTree() {
        List<Tag> rootTags = tagRepository.findByParentIsNullAndIsActiveTrue();
        return rootTags.stream()
                .map(this::buildTagTree)
                .collect(Collectors.toList());
    }

    /**
     * Belirli bir parent'ın child'larını getir
     */
    public List<TagDto> getChildrenByParentId(Long parentId) {
        List<Tag> children = tagRepository.findByParentId(parentId);
        return children.stream()
                .filter(tag -> Boolean.TRUE.equals(tag.getIsActive()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Root tag'leri getir (parent'ı olmayan tag'ler)
     * Frontend'de ilk adım için kullanılır - sadece root tag'ler döner, children yok
     * Bu sayede frontend'de data yığılması önlenir
     */
    public List<TagDto> getRootTags() {
        List<Tag> rootTags = tagRepository.findByParentIsNullAndIsActiveTrue();
        return rootTags.stream()
                .map(this::toDto) // Children olmadan, sadece tag bilgisi
                .collect(Collectors.toList());
    }

    /**
     * Belirli bir tag'in child'larını getir + eğer leaf tag ise product'ları da döndür
     * Frontend'de adım adım tag navigation için kullanılır
     * 
     * Mantık:
     * - Tag'in child'ı varsa → sadece child tag'leri döner
     * - Tag'in child'ı yoksa (leaf tag) → o tag'e ait product'ları döner
     * 
     * Bu sayede frontend:
     * 1. Root tag'leri gösterir
     * 2. Kullanıcı bir tag'e tıklar → bu endpoint çağrılır
     * 3. Eğer child varsa → child tag'leri gösterir (devam eder)
     * 4. Eğer leaf tag ise → product'ları gösterir (son nokta)
     * 
     * ⚡ PERFORMANS İYİLEŞTİRMESİ:
     * - fetch join kullanarak N+1 query problemini önler
     * - Tag children tek query'de çekilir
     * - Product tag ve tag.parent tek query'de çekilir
     */
    public TagWithProductsDto getTagChildrenWithProducts(Long tagId) {
        // Fetch join ile tag'i aktif child'ları ile birlikte getir (N+1 query önlenir)
        Tag tag = tagRepository.findByIdWithActiveChildren(tagId)
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + tagId));

        // Tag'in aktif child'larını kontrol et (zaten fetch edilmiş, lazy loading yok)
        List<Tag> activeChildren = tag.getChildren().stream()
                .filter(child -> Boolean.TRUE.equals(child.getIsActive()))
                .collect(Collectors.toList());

        boolean isLeaf = activeChildren.isEmpty();

        // Child tag'leri DTO'ya çevir (children olmadan, sadece kendileri)
        List<TagDto> childDtos = activeChildren.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        // Eğer leaf tag ise, bu tag'e ait product'ları getir
        // Fetch join ile tag ve tag.parent bilgileri de çekilir (N+1 query önlenir)
        List<ProductResponseDto> products = new ArrayList<>();
        if (isLeaf) {
            products = productRepository.findByTagIdWithTagAndParent(tagId)
                    .stream()
                    .map(ProductMapper::toDto)
                    .collect(Collectors.toList());
        }

        return new TagWithProductsDto(
                tag.getId(),
                tag.getName(),
                tag.getCategoryPath(),
                tag.getParent() != null ? tag.getParent().getId() : null,
                childDtos,
                products,
                isLeaf
        );
    }

    /**
     * Category path'e göre tag getir
     */
    public TagDto getTagByPath(String categoryPath) {
        Tag tag = tagRepository.findByCategoryPath(categoryPath)
                .orElseThrow(() -> new RuntimeException("Tag not found with path: " + categoryPath));
        return buildTagTree(tag);
    }

    /**
     * ID'ye göre tag getir (tree ile birlikte)
     */
    public TagDto getTagById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found with id: " + id));
        return buildTagTree(tag);
    }

    /**
     * Tüm aktif tag'leri flat list olarak getir
     */
    public List<TagDto> getAllTags() {
        return tagRepository.findAll()
                .stream()
                .filter(tag -> Boolean.TRUE.equals(tag.getIsActive()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Tag ismine göre arama yapar (case-insensitive)
     * Hem name hem de categoryPath'te arama yapar
     * Sadece aktif tag'leri döner
     * 
     * @param searchTerm Arama terimi (örn: "iPhone", "Telefon", "Elektronik")
     * @return Bulunan tag'lerin listesi (basit DTO formatında - sadece id, name, categoryPath, parentId)
     */
    public List<TagDto> searchTagsByName(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return getAllTags(); // Boş arama → tüm tag'leri döndür
        }
        
        List<Tag> tags = tagRepository.searchTagsByName(searchTerm.trim());
        return tags.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Tag'i DTO'ya çevir (children olmadan)
     */
    private TagDto toDto(Tag tag) {
        return new TagDto(
                tag.getId(),
                tag.getName(),
                tag.getCategoryPath(),
                tag.getParent() != null ? tag.getParent().getId() : null,
                new ArrayList<>()
        );
    }

    /**
     * Tag'i tree yapısında DTO'ya çevir (recursive)
     */
    private TagDto buildTagTree(Tag tag) {
        TagDto dto = toDto(tag);
        
        // Child'ları recursive olarak ekle
        List<Tag> activeChildren = tag.getChildren().stream()
                .filter(child -> Boolean.TRUE.equals(child.getIsActive()))
                .collect(Collectors.toList());
        
        List<TagDto> childDtos = activeChildren.stream()
                .map(this::buildTagTree)
                .collect(Collectors.toList());
        
        dto.setChildren(childDtos);
        return dto;
    }

    /**
     * Trendyol API'sinden kategorileri çekip Tag'lere dönüştürür ve veritabanına kaydeder
     * Recursive olarak işler: önce root kategorileri, sonra child'ları
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int importTrendyolCategories() {
        log.info("Starting import of Trendyol categories...");
        
        List<TrendyolCategory> trendyolCategories = trendyolCategoryService.fetchAllCategories();
        
        // Trendyol ID -> Tag mapping (parent ilişkilerini kurmak için)
        Map<Integer, Tag> trendyolIdToTagMap = new HashMap<>();
        
        // Mevcut categoryPath'leri önce yükle (duplicate kontrolü için)
        Set<String> existingPaths = tagRepository.findAll().stream()
                .map(Tag::getCategoryPath)
                .collect(Collectors.toSet());
        
        int[] importedCount = {0}; // Array kullanarak final olmayan değişken sorununu çözüyoruz
        
        log.info("Found {} root categories to import from Trendyol", trendyolCategories.size());
        
        // Recursive olarak kategorileri işle (önce parent, sonra child)
        for (TrendyolCategory rootCategory : trendyolCategories) {
            processCategoryRecursive(rootCategory, null, trendyolIdToTagMap, existingPaths, importedCount);
            
            // Her 100 kategori import edildiğinde progress log
            if (importedCount[0] % 100 == 0 && importedCount[0] > 0) {
                log.info("Progress: {} categories imported so far...", importedCount[0]);
            }
        }
        
        log.info("Successfully imported {} categories from Trendyol", importedCount[0]);
        return importedCount[0];
    }

    /**
     * Kategoriyi recursive olarak işler (önce kendisini, sonra child'larını)
     */
    private void processCategoryRecursive(TrendyolCategory trendyolCategory, Tag parentTag, 
                                         Map<Integer, Tag> trendyolIdToTagMap, Set<String> existingPaths, 
                                         int[] importedCount) {
        try {
            // Önce bu kategoriyi oluştur
            Tag tag = convertTrendyolCategoryToTag(trendyolCategory, parentTag, trendyolIdToTagMap, existingPaths);
            
            if (tag != null) {
                trendyolIdToTagMap.put(trendyolCategory.getId(), tag);
                existingPaths.add(tag.getCategoryPath()); // Yeni eklenen path'i de ekle
                importedCount[0]++;
                
                // Sonra child'larını recursive olarak işle
                if (trendyolCategory.getSubCategories() != null && !trendyolCategory.getSubCategories().isEmpty()) {
                    for (TrendyolCategory subCategory : trendyolCategory.getSubCategories()) {
                        processCategoryRecursive(subCategory, tag, trendyolIdToTagMap, existingPaths, importedCount);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to import category {} ({}): {}", 
                    trendyolCategory.getId(), 
                    trendyolCategory.getName(), 
                    e.getMessage());
        }
    }

    /**
     * Trendyol kategorisini Tag'e dönüştürür
     * Her kategori kendi transaction'ında kaydediliyor (connection timeout'u önlemek için)
     */
    private Tag convertTrendyolCategoryToTag(TrendyolCategory trendyolCategory, Tag parentTag, 
                                             Map<Integer, Tag> trendyolIdToTagMap, Set<String> existingPaths) {
        // CategoryPath oluştur
        String categoryPath;
        if (parentTag == null) {
            categoryPath = trendyolCategory.getName();
        } else {
            categoryPath = parentTag.getCategoryPath() + "." + trendyolCategory.getName();
        }
        
        // Eğer bu categoryPath zaten varsa, atla (in-memory duplicate kontrolü)
        if (existingPaths.contains(categoryPath)) {
            log.debug("Tag already exists with categoryPath: {}, skipping", categoryPath);
            return null;
        }
        
        // Tag oluştur - her kategori kendi transaction'ında
        return saveTagInNewTransaction(trendyolCategory.getName(), categoryPath, parentTag);
    }
    
    /**
     * Tag'i yeni bir transaction'da kaydeder (connection timeout'u önlemek için)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Tag saveTagInNewTransaction(String name, String categoryPath, Tag parentTag) {
        // Son bir kez duplicate kontrolü (race condition için)
        if (tagRepository.findByCategoryPath(categoryPath).isPresent()) {
            return null;
        }
        
        Tag tag = new Tag();
        tag.setName(name);
        tag.setCategoryPath(categoryPath);
        tag.setParent(parentTag);
        tag.setCreatedAt(LocalDateTime.now());
        tag.setIsActive(true);
        
        return tagRepository.save(tag);
    }
}


