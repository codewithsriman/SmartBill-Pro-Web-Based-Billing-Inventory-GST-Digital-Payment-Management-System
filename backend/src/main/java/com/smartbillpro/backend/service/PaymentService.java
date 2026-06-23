package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.payment.ConfirmUpiPaymentRequest;
import com.smartbillpro.backend.dto.payment.CreateUpiPaymentRequest;
import com.smartbillpro.backend.dto.payment.PaymentStatusResponse;
import com.smartbillpro.backend.entity.Invoice;
import com.smartbillpro.backend.entity.Payment;
import com.smartbillpro.backend.exception.ResourceNotFoundException;
import com.smartbillpro.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Step 1 of the UPI/QR flow: the cashier selects "UPI / QR Payment" on the Billing page,
     * which creates a PENDING payment record sized to the bill's grand total. The frontend then
     * shows the shop's static QR code (from ShopSettings) alongside this record's ID so the
     * cashier can confirm it once the customer has actually paid.
     */
    @Transactional
    public PaymentStatusResponse createPendingPayment(CreateUpiPaymentRequest request, Long createdByUserId) {
        Payment payment = Payment.builder()
                .amount(request.getAmount())
                .paymentMethod(Invoice.PaymentMethod.UPI)
                .paymentStatus(Payment.Status.PENDING)
                .createdBy(createdByUserId)
                .build();
        payment = paymentRepository.save(payment);
        return toStatusResponse(payment);
    }

    /**
     * Step 2: the cashier clicks "Confirm Payment" after visually verifying the customer's
     * UPI app shows a successful payment. There is no gateway to verify this against — it's a
     * manual attestation, same as a cashier confirming a cash payment was received.
     */
    @Transactional
    public PaymentStatusResponse confirmPayment(Long paymentId, ConfirmUpiPaymentRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        payment.setPaymentStatus(Payment.Status.PAID);
        payment.setTransactionReference(request.getTransactionReference());
        payment = paymentRepository.save(payment);

        return toStatusResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        return toStatusResponse(payment);
    }

    /** Used by BillingService when generating an invoice after a confirmed UPI/QR payment:
     *  returns the Payment entity only if it's genuinely PAID and not already used on another
     *  invoice, so the caller can safely trust its amount. */
    @Transactional(readOnly = true)
    public Payment getVerifiedPaymentEntity(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        if (payment.getPaymentStatus() != Payment.Status.PAID) {
            throw new IllegalStateException(
                    "Payment " + paymentId + " has not been confirmed as PAID yet (status: " + payment.getPaymentStatus() + ")");
        }
        if (payment.getInvoice() != null) {
            throw new IllegalStateException("Payment " + paymentId + " is already linked to another invoice");
        }
        return payment;
    }

    @Transactional
    public Payment linkPaymentToInvoice(Long paymentId, Invoice invoice) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        payment.setInvoice(invoice);
        return paymentRepository.save(payment);
    }

    private PaymentStatusResponse toStatusResponse(Payment payment) {
        return PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .paymentStatus(payment.getPaymentStatus().name())
                .transactionReference(payment.getTransactionReference())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .build();
    }
}
