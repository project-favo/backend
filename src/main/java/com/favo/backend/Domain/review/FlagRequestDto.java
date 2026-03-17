package com.favo.backend.Domain.review;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlagRequestDto {
    private FlagReason reason;
    private String notes;
}

