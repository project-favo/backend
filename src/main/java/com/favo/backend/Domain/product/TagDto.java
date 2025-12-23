package com.favo.backend.Domain.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagDto {
    private Long id;
    private String name;
    private String categoryPath;
    private Long parentId;
    private List<TagDto> children = new ArrayList<>(); // Tree yapısı için
}

