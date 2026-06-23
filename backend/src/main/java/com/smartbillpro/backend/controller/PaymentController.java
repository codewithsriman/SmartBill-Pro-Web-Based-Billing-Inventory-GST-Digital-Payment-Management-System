package com.smartbillpro.backend.controller;

import com.smartbillpro.backend.dto.common.ApiResponse;
import com.smartbillpro.backend.dto.payment.ConfirmUpiPaymentRequest;
import com.smartbillpro.backend.dto.payment.CreateUpiPaymentRequest;
import com.smartbillpro.backend.dto.payment.PaymentStatusResponse;
import com.smartbillpro.backend.security.UserPrincipal;
import com.smartbillpro.backend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Step 1 of the UPI/QR flow: creates a PENDING payment record sized to the bill total.
     *  The frontend then displays the shop's static QR code alongside this. */
    @PostMapping("/upi/initiate")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> initiateUpiPayment(
            @Valid @RequestBody CreateUpiPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PaymentStatusResponse response = paymentService.createPendingPayment(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Step 2: cashier manually confirms the customer has paid via their UPI app. */
    @PostMapping("/upi/{paymentId}/confirm")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> confirmUpiPayment(
            @PathVariable Long paymentId,
            @RequestBody(required = false) ConfirmUpiPaymentRequest request) {
        ConfirmUpiPaymentRequest body = request != null ? request : new ConfirmUpiPaymentRequest();
        PaymentStatusResponse response = paymentService.confirmPayment(paymentId, body);
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed", response));
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentStatus(paymentId)));
    }
}
