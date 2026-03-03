package com.favo.backend.Domain.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSummaryDto {

    private Long id;
    private String username;
    private String profilePhotoUrl;
}

