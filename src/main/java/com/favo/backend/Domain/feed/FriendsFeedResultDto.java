package com.favo.backend.Domain.feed;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FriendsFeedResultDto {
    private List<FriendsFeedItemDto> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number; // 0-based page index
}
