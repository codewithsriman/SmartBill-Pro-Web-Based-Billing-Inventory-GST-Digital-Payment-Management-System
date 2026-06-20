package com.smartbillpro.backend.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.smartbillpro.backend.dto.payment.CreateRazorpayOrderRequest;
import com.smartbillpro.backend.dto.payment.PaymentStatusResponse;
import com.smartbillpro.backend.dto.payment.RazorpayOrderResponse;
import com.smartbillpro.backend.dto.payment.VerifyRazorpayPaymentRequest;
import com.smartbillpro.backend.entity.Invoice;
import com.smartbillpro.backend.entity.Payment;
import com.smartbillpro.backend.exception.ResourceNotFoundException;
import com.smartbillpro.backend.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class RazorpayService {

    private final PaymentRepository paymentRepository;

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;

    private static final BigDecimal PAISE_MULTIPLIER = BigDecimal.valueOf(100);

    public RazorpayService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Creates a Razorpay order for the given amount and records a PENDING Payment row.
     * The invoice doesn't exist yet at this point — it's created only after the payment
     * is verified as successful (see BillingService.createInvoice with paymentId set).
     */
    @Transactional
    public RazorpayOrderResponse createOrder(CreateRazorpayOrderRequest request, Long createdByUserId) {
        if (isPlaceholderConfigured()) {
            throw new IllegalStateException(
                    "Razorpay keys are not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET " +
                    "environment variables with your real Razorpay test/live keys before accepting online payments.");
        }

        Invoice.PaymentMethod method = parsePaymentMethod(request.getPaymentMethod());

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            // Razorpay expects amounts in the smallest currency unit (paise for INR)
            int amountInPaise = request.getAmount()
                    .multiply(PAISE_MULTIPLIER)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "sb_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1); // auto-capture on successful authorization

            com.razorpay.Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            Payment payment = Payment.builder()
                    .amount(request.getAmount())
                    .paymentMethod(method)
                    .paymentStatus(Payment.Status.PENDING)
                    .razorpayOrderId(razorpayOrderId)
                    .createdBy(createdByUserId)
                    .build();
            payment = paymentRepository.save(payment);

            return RazorpayOrderResponse.builder()
                    .paymentId(payment.getId())
                    .razorpayOrderId(razorpayOrderId)
                    .razorpayKeyId(keyId)
                    .amount(request.getAmount())
                    .currency("INR")
                    .build();

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order", e);
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the HMAC-SHA256 signature Razorpay's Checkout returns after a successful payment,
     * using the official SDK's Utils.verifyPaymentSignature — this is the step that actually
     * proves the payment is genuine and wasn't forged client-side.
     */
    @Transactional
    public PaymentStatusResponse verifyPayment(VerifyRazorpayPaymentRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", request.getPaymentId()));

        if (!payment.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
            throw new IllegalArgumentException("Order ID does not match the payment record");
        }

        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(attributes, keySecret);

            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setTransactionId(request.getRazorpayPaymentId());

            if (isValid) {
                payment.setPaymentStatus(Payment.Status.PAID);
                log.info("Razorpay payment verified successfully: {}", request.getRazorpayPaymentId());
            } else {
                payment.setPaymentStatus(Payment.Status.FAILED);
                log.warn("Razorpay signature verification FAILED for order {}", request.getRazorpayOrderId());
            }

            payment = paymentRepository.save(payment);
            return toStatusResponse(payment);

        } catch (RazorpayException e) {
            log.error("Error verifying Razorpay signature", e);
            payment.setPaymentStatus(Payment.Status.FAILED);
            paymentRepository.save(payment);
            throw new RuntimeException("Payment verification failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        return toStatusResponse(payment);
    }

    /** Used by BillingService when generating an invoice after online payment: returns the
     *  Payment entity only if it's genuinely in PAID status, so the caller can safely trust
     *  its amount. Throws if the payment doesn't exist, isn't PAID, or is already linked
     *  to a different invoice (prevents double-spending one payment across two invoices). */
    @Transactional(readOnly = true)
    public Payment getVerifiedPaymentEntity(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        if (payment.getPaymentStatus() != Payment.Status.PAID) {
            throw new IllegalStateException(
                    "Payment " + paymentId + " is not verified as PAID (status: " + payment.getPaymentStatus() + ")");
        }
        if (payment.getInvoice() != null) {
            throw new IllegalStateException("Payment " + paymentId + " is already linked to another invoice");
        }
        return payment;
    }

    /** Used by BillingService to attach a verified PAID payment to the newly-created invoice. */
    @Transactional
    public Payment linkPaymentToInvoice(Long paymentId, Invoice invoice) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        if (payment.getPaymentStatus() != Payment.Status.PAID) {
            throw new IllegalStateException("Cannot generate invoice: payment " + paymentId + " is not in PAID status");
        }
        payment.setInvoice(invoice);
        return paymentRepository.save(payment);
    }

    private boolean isPlaceholderConfigured() {
        return keyId == null || keyId.contains("placeholder") || keySecret == null || keySecret.contains("placeholder");
    }

    private Invoice.PaymentMethod parsePaymentMethod(String value) {
        try {
            return Invoice.PaymentMethod.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payment method: " + value);
        }
    }

    private PaymentStatusResponse toStatusResponse(Payment payment) {
        return PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .paymentStatus(payment.getPaymentStatus().name())
                .transactionId(payment.getTransactionId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .build();
    }
}
