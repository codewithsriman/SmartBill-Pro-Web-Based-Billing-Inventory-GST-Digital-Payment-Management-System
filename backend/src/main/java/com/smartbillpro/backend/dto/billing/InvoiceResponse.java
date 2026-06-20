package com.smartbillpro.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class InvoiceResponse {

    private Long id;
    private String invoiceNumber;
    private LocalDate invoiceDate;

    private Long customerId;
    private String customerName;
    private String customerPhone;

    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal gstTotal;
    private BigDecimal grandTotal;
    private BigDecimal amountPaid;
    private BigDecimal balanceDue;

    private String paymentMethod;
    private String paymentStatus;
    private String status;
    private String notes;

    private List<InvoiceItemResponse> items;

    private LocalDateTime createdAt;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    public static class InvoiceItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal pricePerUnit;
        private BigDecimal gstPercentage;
        private BigDecimal gstAmount;
        private BigDecimal lineTotal;
    }
}
