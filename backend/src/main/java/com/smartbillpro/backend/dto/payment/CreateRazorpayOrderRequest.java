package com.smartbillpro.backend.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateRazorpayOrderRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    private BigDecimal amount; // grand total of the in-progress bill, in rupees

    @NotNull(message = "Payment method is required")
    private String paymentMethod; // UPI, QR, DEBIT_CARD, CREDIT_CARD
}
