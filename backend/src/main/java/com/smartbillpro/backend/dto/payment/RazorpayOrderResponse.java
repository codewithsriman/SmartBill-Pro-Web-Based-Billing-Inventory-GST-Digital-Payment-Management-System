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
public class RazorpayOrderResponse {
    private Long paymentId;          // our internal payments.id, used to look up status later
    private String razorpayOrderId;
    private String razorpayKeyId;    // public key, safe to expose to the frontend Checkout SDK
    private BigDecimal amount;
    private String currency;
}
