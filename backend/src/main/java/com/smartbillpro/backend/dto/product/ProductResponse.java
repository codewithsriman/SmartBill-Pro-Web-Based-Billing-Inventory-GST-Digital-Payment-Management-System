package com.smartbillpro.backend.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String productName;
    private String barcode;
    private String qrCodeData;
    private String qrCodeImageUrl;
    private Long categoryId;
    private String categoryName;
    private String unit;
    private BigDecimal price;
    private BigDecimal gstPercentage;
    private BigDecimal stockQuantity;
    private BigDecimal reorderLevel;
    private boolean lowStock;
    private boolean active;
}
