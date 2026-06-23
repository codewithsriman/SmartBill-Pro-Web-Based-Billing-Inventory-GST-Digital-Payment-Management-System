package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.billing.CreateInvoiceRequest;
import com.smartbillpro.backend.dto.billing.InvoiceResponse;
import com.smartbillpro.backend.dto.billing.UpdatePaymentRequest;
import com.smartbillpro.backend.entity.*;
import com.smartbillpro.backend.exception.InsufficientStockException;
import com.smartbillpro.backend.exception.ResourceNotFoundException;
import com.smartbillpro.backend.repository.*;
import com.smartbillpro.backend.util.GstCalculator;
import com.smartbillpro.backend.util.InvoiceNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final PaymentService paymentService;

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request, Long createdByUserId) {

        // Resolve invoice prefix from company settings (fallback to "INV")
        String prefix = companySettingsRepository.findAll().stream()
                .findFirst()
                .map(CompanySettings::getInvoicePrefix)
                .orElse("INV");

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));
        }

        Invoice.InvoiceStatus invoiceStatus = request.isFinalize()
                ? Invoice.InvoiceStatus.DRAFT // set DRAFT first; flipped to FINALIZED after items+stock succeed below
                : Invoice.InvoiceStatus.DRAFT;

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumberGenerator.generate(prefix))
                .invoiceDate(request.getInvoiceDate())
                .customer(customer)
                .customerNameSnap(request.getCustomerName())
                .customerPhoneSnap(request.getCustomerPhone())
                .discountAmount(request.getDiscountAmount() == null ? BigDecimal.ZERO : request.getDiscountAmount())
                .paymentMethod(parsePaymentMethod(request.getPaymentMethod()))
                .notes(request.getNotes())
                .status(invoiceStatus)
                .createdBy(createdByUserId)
                .build();

        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;

        for (CreateInvoiceRequest.InvoiceItemRequest itemReq : request.getItems()) {
            Product product = null;
            if (itemReq.getProductId() != null) {
                product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

                if (request.isFinalize() && product.getStockQuantity().compareTo(itemReq.getQuantity()) < 0) {
                    throw new InsufficientStockException(
                            "Insufficient stock for '" + product.getProductName() + "'. Available: "
                                    + product.getStockQuantity() + ", requested: " + itemReq.getQuantity());
                }
            }

            BigDecimal base = GstCalculator.lineBaseAmount(itemReq.getQuantity(), itemReq.getPricePerUnit());
            BigDecimal gstAmt = GstCalculator.lineGstAmount(base, itemReq.getGstPercentage());
            BigDecimal lineTotal = GstCalculator.lineTotal(base, gstAmt);

            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .productNameSnap(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .unit(itemReq.getUnit())
                    .pricePerUnit(itemReq.getPricePerUnit())
                    .gstPercentage(itemReq.getGstPercentage())
                    .gstAmount(gstAmt)
                    .lineTotal(lineTotal)
                    .build();

            items.add(item);
            subtotal = subtotal.add(base);
            totalGst = totalGst.add(gstAmt);

            // Deduct stock only when finalizing (drafts don't touch inventory)
            if (request.isFinalize() && product != null) {
                product.setStockQuantity(product.getStockQuantity().subtract(itemReq.getQuantity()));
                productRepository.save(product);
            }
        }

        invoice.setItems(items);
        invoice.setSubtotal(GstCalculator.round(subtotal));

        BigDecimal[] cgstSgst = GstCalculator.splitCgstSgst(totalGst);
        invoice.setCgstAmount(cgstSgst[0]);
        invoice.setSgstAmount(cgstSgst[1]);
        invoice.setIgstAmount(BigDecimal.ZERO); // intra-state default; switch to IGST split for inter-state customers
        invoice.setGstTotal(GstCalculator.round(totalGst));

        BigDecimal grandTotal = subtotal.add(totalGst).subtract(invoice.getDiscountAmount());
        invoice.setGrandTotal(GstCalculator.round(grandTotal));

        // For UPI/QR payments, the amount paid and PAID status come from the
        // server-verified Payment record — never trust client-supplied amountPaid here, since
        // that would let a client claim "fully paid" without an actual verified transaction.
        Payment verifiedPayment = null;
        if (request.getPaymentId() != null) {
            verifiedPayment = paymentService.getVerifiedPaymentEntity(request.getPaymentId());
            invoice.setAmountPaid(GstCalculator.round(verifiedPayment.getAmount()));
        } else {
            BigDecimal amountPaid = request.getAmountPaid() == null ? BigDecimal.ZERO : request.getAmountPaid();
            invoice.setAmountPaid(GstCalculator.round(amountPaid));
        }

        BigDecimal balanceDue = invoice.getGrandTotal().subtract(invoice.getAmountPaid());
        invoice.setBalanceDue(GstCalculator.round(balanceDue.max(BigDecimal.ZERO)));

        invoice.setPaymentStatus(resolvePaymentStatus(invoice.getGrandTotal(), invoice.getAmountPaid()));

        if (request.isFinalize()) {
            invoice.setStatus(Invoice.InvoiceStatus.FINALIZED);
        }

        // Track outstanding balance on the customer record for credit sales
        if (customer != null && request.isFinalize() && invoice.getBalanceDue().compareTo(BigDecimal.ZERO) > 0) {
            customer.setOutstandingBalance(customer.getOutstandingBalance().add(invoice.getBalanceDue()));
            customerRepository.save(customer);
        }

        Invoice saved = invoiceRepository.save(invoice);

        if (verifiedPayment != null) {
            paymentService.linkPaymentToInvoice(verifiedPayment.getId(), saved);
        }

        return toResponse(saved);
    }

    @Transactional
    public InvoiceResponse recordPayment(Long invoiceId, UpdatePaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        BigDecimal newAmountPaid = invoice.getAmountPaid().add(request.getAmount());
        invoice.setAmountPaid(GstCalculator.round(newAmountPaid));

        BigDecimal newBalance = invoice.getGrandTotal().subtract(invoice.getAmountPaid());
        invoice.setBalanceDue(GstCalculator.round(newBalance.max(BigDecimal.ZERO)));
        invoice.setPaymentStatus(resolvePaymentStatus(invoice.getGrandTotal(), invoice.getAmountPaid()));
        invoice.setPaymentMethod(parsePaymentMethod(request.getPaymentMethod()));

        if (invoice.getCustomer() != null) {
            Customer customer = invoice.getCustomer();
            BigDecimal updatedOutstanding = customer.getOutstandingBalance().subtract(request.getAmount());
            customer.setOutstandingBalance(updatedOutstanding.max(BigDecimal.ZERO));
            customerRepository.save(customer);
        }

        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByNumber(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "invoiceNumber", invoiceNumber));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> searchInvoices(String keyword, Pageable pageable) {
        Page<Invoice> page = (keyword == null || keyword.isBlank())
                ? invoiceRepository.findRecent(pageable)
                : invoiceRepository.search(keyword, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public void cancelInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        // Restock items if the invoice had been finalized (stock was deducted)
        if (invoice.getStatus() == Invoice.InvoiceStatus.FINALIZED) {
            for (InvoiceItem item : invoice.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity().add(item.getQuantity()));
                    productRepository.save(product);
                }
            }
        }

        invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    private Invoice.PaymentStatus resolvePaymentStatus(BigDecimal grandTotal, BigDecimal amountPaid) {
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            return Invoice.PaymentStatus.PENDING;
        } else if (amountPaid.compareTo(grandTotal) >= 0) {
            return Invoice.PaymentStatus.PAID;
        } else {
            return Invoice.PaymentStatus.PARTIAL;
        }
    }

    private Invoice.PaymentMethod parsePaymentMethod(String value) {
        try {
            return Invoice.PaymentMethod.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payment method: " + value);
        }
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceResponse.InvoiceItemResponse> itemResponses = invoice.getItems().stream()
                .map(item -> InvoiceResponse.InvoiceItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                        .productName(item.getProductNameSnap())
                        .quantity(item.getQuantity())
                        .unit(item.getUnit())
                        .pricePerUnit(item.getPricePerUnit())
                        .gstPercentage(item.getGstPercentage())
                        .gstAmount(item.getGstAmount())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceDate(invoice.getInvoiceDate())
                .customerId(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null)
                .customerName(invoice.getCustomerNameSnap())
                .customerPhone(invoice.getCustomerPhoneSnap())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .cgstAmount(invoice.getCgstAmount())
                .sgstAmount(invoice.getSgstAmount())
                .igstAmount(invoice.getIgstAmount())
                .gstTotal(invoice.getGstTotal())
                .grandTotal(invoice.getGrandTotal())
                .amountPaid(invoice.getAmountPaid())
                .balanceDue(invoice.getBalanceDue())
                .paymentMethod(invoice.getPaymentMethod().name())
                .paymentStatus(invoice.getPaymentStatus().name())
                .status(invoice.getStatus().name())
                .notes(invoice.getNotes())
                .items(itemResponses)
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
