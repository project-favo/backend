package com.favo.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Product catalog, search, and comparison (range 13000–13999).
 */
public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND, 13001, "The requested product was not found.",
            ErrorSeverity.WARN, false, true),
    /**
     * Internal catalog / seed file JSON could not be parsed or mapped to DTOs.
     */
    CATALOG_IMPORT_PARSE_ERROR(
            HttpStatus.BAD_REQUEST, 13002, "Could not parse catalog import payload as valid JSON/structure.",
            ErrorSeverity.ERROR, false, false),
    /**
     * Another product with the same barcode or GTIN already exists.
     */
    PRODUCT_DUPLICATE_BARCODE(
            HttpStatus.CONFLICT, 13003, "A product with this barcode or GTIN already exists.",
            ErrorSeverity.WARN, false, true),
    /**
     * Client requested comparison of more than the supported maximum (e.g. four) products.
     */
    COMPARISON_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, 13004, "The number of products to compare exceeds the allowed maximum.",
            ErrorSeverity.WARN, false, true),
    PRODUCT_IMAGE_FETCH_FAILED(
            HttpStatus.BAD_GATEWAY, 13005, "Could not load or process the primary product image from upstream storage.",
            ErrorSeverity.WARN, true, true),
    CATALOG_IMPORT_SCHEMA_INVALID(
            HttpStatus.BAD_REQUEST, 13006, "Catalog import data failed required field or schema checks.",
            ErrorSeverity.ERROR, false, false),
    /**
     * Internal SKU/identifier used by a catalog feed conflicts with an existing product.
     */
    PRODUCT_SKU_CONFLICT(
            HttpStatus.CONFLICT, 13007, "A product with this internal SKU or identifier already exists.",
            ErrorSeverity.WARN, false, true),
    PRODUCT_SEARCH_QUERY_INVALID(
            HttpStatus.BAD_REQUEST, 13008, "Search or filter query parameters are not valid for this API.",
            ErrorSeverity.WARN, false, true),
    PRODUCT_UNAVAILABLE(
            HttpStatus.GONE, 13009, "The product is no longer available in the catalog.",
            ErrorSeverity.WARN, false, true);

    private final HttpStatus httpStatus;
    private final int internalCode;
    private final String message;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final boolean exposedToClient;

    ProductErrorCode(HttpStatus httpStatus, int internalCode, String message, ErrorSeverity severity,
            boolean retryable, boolean exposedToClient) {
        this.httpStatus = httpStatus;
        this.internalCode = internalCode;
        this.message = message;
        this.severity = severity;
        this.retryable = retryable;
        this.exposedToClient = exposedToClient;
    }

    @Override
    public String getErrorCode() {
        return name();
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public int getInternalCode() {
        return internalCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ErrorSeverity getSeverity() {
        return severity;
    }

    @Override
    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public boolean isExposedToClient() {
        return exposedToClient;
    }
}
