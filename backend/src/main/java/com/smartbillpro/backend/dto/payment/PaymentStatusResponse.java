package com.smartbillpro.backend.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class PaymentStatusResponse {
    private Long paymentId;
    private String paymentStatus;       // PENDING, PAID, FAILED
    private String transactionReference; // optional, cashier-entered after confirming a UPI/QR payment
    private BigDecimal amount;
    private String paymentMethod;
}
