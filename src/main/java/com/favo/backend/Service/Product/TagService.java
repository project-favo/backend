package com.favo.backend.Service.Product;

import com.favo.backend.Domain.product.Tag;
import com.favo.backend.Domain.product.TagDto;
import com.favo.backend.Domain.product.Repository.TagRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final TagRepository tagRepository;

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
}

