package com.smartbillpro.backend.controller;

import com.smartbillpro.backend.dto.billing.CreateInvoiceRequest;
import com.smartbillpro.backend.dto.billing.InvoiceResponse;
import com.smartbillpro.backend.dto.billing.UpdatePaymentRequest;
import com.smartbillpro.backend.dto.common.ApiResponse;
import com.smartbillpro.backend.security.UserPrincipal;
import com.smartbillpro.backend.service.BillingService;
import com.smartbillpro.backend.service.InvoicePdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final InvoicePdfService invoicePdfService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        InvoiceResponse response = billingService.createInvoice(request, currentUser.getId());
        String message = request.isFinalize() ? "Invoice created successfully" : "Draft saved successfully";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getInvoice(id)));
    }

    @GetMapping("/by-number/{invoiceNumber}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceByNumber(@PathVariable String invoiceNumber) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getInvoiceByNumber(invoiceNumber)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> searchInvoices(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(billingService.searchInvoices(keyword, pageable)));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<InvoiceResponse>> recordPayment(
            @PathVariable Long id, @Valid @RequestBody UpdatePaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", billingService.recordPayment(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> cancelInvoice(@PathVariable Long id) {
        billingService.cancelInvoice(id);
        return ResponseEntity.ok(ApiResponse.success("Invoice cancelled and stock restored", null));
    }

    /** Used by the Invoice Sharing module's "Download PDF" / "Print Invoice" actions. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id) {
        InvoiceResponse invoice = billingService.getInvoice(id);
        byte[] pdfBytes = invoicePdfService.generatePdfBytes(invoice);

        String filename = invoice.getInvoiceNumber().replace("/", "_") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(pdfBytes);
    }
}
