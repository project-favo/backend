package com.favo.backend.Domain.catalogimport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogImportFileDto {
    private List<ImportTagRow> tags;
    private List<ImportProductRow> products;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImportTagRow {
        private Long id;
        private String name;
        private String categoryPath;
        private Long parentId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImportProductRow {
        private String name;
        private String description;
        private String imageURL;
        private TagRef tag;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class TagRef {
            private Long id;
        }
    }
}
