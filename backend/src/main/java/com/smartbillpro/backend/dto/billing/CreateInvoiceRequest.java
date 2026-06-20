package com.smartbillpro.backend.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateInvoiceRequest {

    private Long customerId; // optional - walk-in customer if null

    @NotBlank(message = "Customer name is required")
    private String customerName;

    private String customerPhone;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    @NotEmpty(message = "Invoice must contain at least one item")
    @Valid
    private List<InvoiceItemRequest> items;

    @NotNull(message = "Discount amount is required (use 0 if none)")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull(message = "Payment method is required")
    private String paymentMethod; // CASH, CREDIT, UPI, QR, DEBIT_CARD, CREDIT_CARD

    @NotNull(message = "Amount paid is required (use 0 for fully pending)")
    private BigDecimal amountPaid = BigDecimal.ZERO;

    private String notes;

    /** true = finalize and deduct stock immediately, false = save as draft without stock impact */
    private boolean finalize = true;

    /** Set when this invoice is being generated after a successful online (Razorpay) payment —
     *  links the invoice back to the payments row and copies its captured amount/status across. */
    private Long paymentId;

    @Getter
    @Setter
    public static class InvoiceItemRequest {
        private Long productId; // optional - allows manual line items not tied to inventory

        @NotBlank(message = "Product name is required")
        private String productName;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;

        private String unit;

        @NotNull(message = "Price per unit is required")
        private BigDecimal pricePerUnit;

        @NotNull(message = "GST percentage is required")
        private BigDecimal gstPercentage = BigDecimal.ZERO;
    }
}
