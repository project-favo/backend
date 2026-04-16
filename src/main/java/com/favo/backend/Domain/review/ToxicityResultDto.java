package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ToxicityResultDto {
    /**
     * Moderasyon için kullanılan birleşik skor: HF cevabındaki toxicity başlıkları arasından
     * en yüksek olasılık (tek etiket değil, çok başlıklı model çıktısının özeti).
     */
    private final Double toxicScore;
    private final boolean isToxic;
}

