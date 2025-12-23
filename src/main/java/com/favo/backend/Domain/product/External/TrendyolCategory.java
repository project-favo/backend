package com.favo.backend.Domain.product.External;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrendyolCategory {
    
    @JsonProperty("id")
    private Integer id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("parentId")
    private Integer parentId;
    
    @JsonProperty("subCategories")
    private List<TrendyolCategory> subCategories = new ArrayList<>();
}

