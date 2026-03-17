package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ToxicityResultDto {
    private final Double toxicScore;
    private final boolean isToxic;
}

