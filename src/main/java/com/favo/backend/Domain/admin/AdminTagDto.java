package com.favo.backend.Domain.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin tag listesi için DTO (isActive dahil).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminTagDto {
    private Long id;
    private String name;
    private String categoryPath;
    private Long parentId;
    private Boolean isActive;
}
