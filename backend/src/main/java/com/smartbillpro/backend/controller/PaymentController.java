package com.smartbillpro.backend.controller;

import com.smartbillpro.backend.dto.common.ApiResponse;
import com.smartbillpro.backend.dto.payment.CreateRazorpayOrderRequest;
import com.smartbillpro.backend.dto.payment.PaymentStatusResponse;
import com.smartbillpro.backend.dto.payment.RazorpayOrderResponse;
import com.smartbillpro.backend.dto.payment.VerifyRazorpayPaymentRequest;
import com.smartbillpro.backend.security.UserPrincipal;
import com.smartbillpro.backend.service.RazorpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RazorpayService razorpayService;

    /** Step 1 of the online payment flow: create a Razorpay order for the bill's grand total. */
    @PostMapping("/razorpay/order")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createOrder(
            @Valid @RequestBody CreateRazorpayOrderRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        RazorpayOrderResponse response = razorpayService.createOrder(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Step 2: called by the frontend after Razorpay Checkout returns a successful payment,
     *  to verify the signature server-side before trusting the payment. */
    @PostMapping("/razorpay/verify")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> verifyPayment(
            @Valid @RequestBody VerifyRazorpayPaymentRequest request) {
        PaymentStatusResponse response = razorpayService.verifyPayment(request);
        String message = "PAID".equals(response.getPaymentStatus())
                ? "Payment verified successfully"
                : "Payment verification failed";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success(razorpayService.getPaymentStatus(paymentId)));
    }
}
