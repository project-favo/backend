package com.favo.backend.Domain.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Admin list endpoint'leri için sayfalı response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminPageDto<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;
}
