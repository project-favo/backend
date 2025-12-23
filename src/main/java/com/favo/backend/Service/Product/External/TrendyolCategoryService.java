package com.favo.backend.Service.Product.External;

import com.favo.backend.Domain.product.External.TrendyolCategory;
import com.favo.backend.Domain.product.External.TrendyolCategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendyolCategoryService {

    private static final String TRENDYOL_CATEGORIES_URL = "https://apigw.trendyol.com/integration/product/product-categories";
    
    private final RestTemplate restTemplate;

    /**
     * Trendyol API'sinden tüm kategorileri çeker
     */
    public List<TrendyolCategory> fetchAllCategories() {
        try {
            log.info("Fetching categories from Trendyol API: {}", TRENDYOL_CATEGORIES_URL);
            
            ResponseEntity<TrendyolCategoryResponse> response = restTemplate.getForEntity(
                    TRENDYOL_CATEGORIES_URL,
                    TrendyolCategoryResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<TrendyolCategory> categories = response.getBody().getCategories();
                log.info("Successfully fetched {} categories from Trendyol", categories.size());
                return categories;
            } else {
                log.error("Failed to fetch categories from Trendyol. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to fetch categories from Trendyol API");
            }
        } catch (Exception e) {
            log.error("Error fetching categories from Trendyol API", e);
            throw new RuntimeException("Error fetching categories from Trendyol API: " + e.getMessage(), e);
        }
    }
}

