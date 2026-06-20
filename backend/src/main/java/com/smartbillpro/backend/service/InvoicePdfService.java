package com.smartbillpro.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartbillpro.backend.dto.billing.InvoiceResponse;
import com.smartbillpro.backend.entity.CompanySettings;
import com.smartbillpro.backend.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final CompanySettingsRepository companySettingsRepository;

    @Value("${app.upload.invoice-pdf-dir}")
    private String invoicePdfDir;

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(30, 41, 59));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 100));
    private static final Color BRAND_COLOR = new Color(37, 99, 235); // matches frontend primary blue

    /** Generates the invoice PDF in-memory and returns the raw bytes (for direct download responses). */
    public byte[] generatePdfBytes(InvoiceResponse invoice) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            buildDocument(invoice, baos);
            return baos.toByteArray();
        } catch (DocumentException e) {
            log.error("Failed to generate invoice PDF for {}", invoice.getInvoiceNumber(), e);
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    /** Generates the PDF and saves it to disk, returning the relative path for the Share Invoice module. */
    public String generateAndSave(InvoiceResponse invoice) {
        byte[] bytes = generatePdfBytes(invoice);
        try {
            Path dir = Path.of(invoicePdfDir);
            Files.createDirectories(dir);
            String filename = invoice.getInvoiceNumber().replace("/", "_") + ".pdf";
            Path filePath = dir.resolve(filename);
            Files.write(filePath, bytes);
            return "invoices/" + filename;
        } catch (IOException e) {
            log.error("Failed to save invoice PDF to disk for {}", invoice.getInvoiceNumber(), e);
            throw new RuntimeException("Failed to save invoice PDF", e);
        }
    }

    private void buildDocument(InvoiceResponse invoice, ByteArrayOutputStream out) throws DocumentException {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();

        CompanySettings company = companySettingsRepository.findAll().stream().findFirst().orElse(null);

        addHeader(document, company);
        addInvoiceMeta(document, invoice);
        addCustomerDetails(document, invoice);
        addItemsTable(document, invoice);
        addTotals(document, invoice);
        addPaymentInfo(document, invoice);
        addFooter(document, company);

        document.close();
    }

    private void addHeader(Document document, CompanySettings company) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2, 1});

        String companyName = company != null ? company.getCompanyName() : "Your Company Name";
        String gst = company != null ? company.getCompanyGstNumber() : "";
        String address = company != null ? company.getCompanyAddress() : "";
        String phone = company != null ? company.getCompanyPhone() : "";
        String email = company != null ? company.getCompanyEmail() : "";

        Paragraph companyBlock = new Paragraph();
        companyBlock.add(new Chunk(companyName + "\n", TITLE_FONT));
        if (address != null && !address.isBlank()) companyBlock.add(new Chunk(address + "\n", SMALL_FONT));
        if (phone != null && !phone.isBlank()) companyBlock.add(new Chunk("Phone: " + phone + "\n", SMALL_FONT));
        if (email != null && !email.isBlank()) companyBlock.add(new Chunk("Email: " + email + "\n", SMALL_FONT));
        if (gst != null && !gst.isBlank()) companyBlock.add(new Chunk("GSTIN: " + gst, SMALL_FONT));

        PdfPCell leftCell = new PdfPCell(companyBlock);
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setVerticalAlignment(Element.ALIGN_TOP);

        Paragraph invoiceTitle = new Paragraph("TAX INVOICE", new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_COLOR));
        invoiceTitle.setAlignment(Element.ALIGN_RIGHT);
        PdfPCell rightCell = new PdfPCell(invoiceTitle);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setVerticalAlignment(Element.ALIGN_TOP);

        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        document.add(headerTable);

        document.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(1f, 100f, BRAND_COLOR, Element.ALIGN_CENTER, -2)));
        document.add(Chunk.NEWLINE);
    }

    private void addInvoiceMeta(Document document, InvoiceResponse invoice) throws DocumentException {
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingAfter(10f);

        addPlainCell(metaTable, "Invoice Number: " + invoice.getInvoiceNumber(), BOLD_FONT, Element.ALIGN_LEFT);
        addPlainCell(metaTable, "Invoice Date: " + invoice.getInvoiceDate(), BOLD_FONT, Element.ALIGN_RIGHT);

        document.add(metaTable);
    }

    private void addCustomerDetails(Document document, InvoiceResponse invoice) throws DocumentException {
        Paragraph billTo = new Paragraph("Bill To:", BOLD_FONT);
        document.add(billTo);
        Paragraph customer = new Paragraph();
        customer.add(new Chunk((invoice.getCustomerName() != null ? invoice.getCustomerName() : "Walk-in Customer") + "\n", NORMAL_FONT));
        if (invoice.getCustomerPhone() != null && !invoice.getCustomerPhone().isBlank()) {
            customer.add(new Chunk("Phone: " + invoice.getCustomerPhone(), NORMAL_FONT));
        }
        customer.setSpacingAfter(10f);
        document.add(customer);
    }

    private void addItemsTable(Document document, InvoiceResponse invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 1, 1, 1.2f, 1, 1.3f});
        table.setSpacingBefore(5f);

        String[] headers = {"Item", "Qty", "Unit", "Price", "GST %", "Amount"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(BRAND_COLOR);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        boolean alternate = false;
        for (InvoiceResponse.InvoiceItemResponse item : invoice.getItems()) {
            Color bg = alternate ? new Color(248, 250, 252) : Color.WHITE;
            addBodyCell(table, item.getProductName(), bg, Element.ALIGN_LEFT);
            addBodyCell(table, item.getQuantity().stripTrailingZeros().toPlainString(), bg, Element.ALIGN_CENTER);
            addBodyCell(table, item.getUnit() == null ? "-" : item.getUnit(), bg, Element.ALIGN_CENTER);
            addBodyCell(table, formatMoney(item.getPricePerUnit()), bg, Element.ALIGN_RIGHT);
            addBodyCell(table, item.getGstPercentage().stripTrailingZeros().toPlainString() + "%", bg, Element.ALIGN_CENTER);
            addBodyCell(table, formatMoney(item.getLineTotal()), bg, Element.ALIGN_RIGHT);
            alternate = !alternate;
        }

        document.add(table);
    }

    private void addTotals(Document document, InvoiceResponse invoice) throws DocumentException {
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(10f);

        addTotalRow(totals, "Subtotal", invoice.getSubtotal(), NORMAL_FONT);
        addTotalRow(totals, "Discount", invoice.getDiscountAmount(), NORMAL_FONT);
        addTotalRow(totals, "CGST", invoice.getCgstAmount(), NORMAL_FONT);
        addTotalRow(totals, "SGST", invoice.getSgstAmount(), NORMAL_FONT);
        if (invoice.getIgstAmount() != null && invoice.getIgstAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "IGST", invoice.getIgstAmount(), NORMAL_FONT);
        }
        addTotalRow(totals, "Grand Total", invoice.getGrandTotal(), new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_COLOR));
        addTotalRow(totals, "Amount Paid", invoice.getAmountPaid(), NORMAL_FONT);
        addTotalRow(totals, "Balance Due", invoice.getBalanceDue(), BOLD_FONT);

        document.add(totals);
    }

    private void addPaymentInfo(Document document, InvoiceResponse invoice) throws DocumentException {
        Paragraph payment = new Paragraph();
        payment.setSpacingBefore(12f);
        payment.add(new Chunk("Payment Method: ", BOLD_FONT));
        payment.add(new Chunk(invoice.getPaymentMethod() + "\n", NORMAL_FONT));
        payment.add(new Chunk("Payment Status: ", BOLD_FONT));
        payment.add(new Chunk(invoice.getPaymentStatus(), NORMAL_FONT));
        document.add(payment);

        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            Paragraph notes = new Paragraph();
            notes.setSpacingBefore(8f);
            notes.add(new Chunk("Notes: ", BOLD_FONT));
            notes.add(new Chunk(invoice.getNotes(), NORMAL_FONT));
            document.add(notes);
        }
    }

    private void addFooter(Document document, CompanySettings company) throws DocumentException {
        Paragraph footer = new Paragraph("Thank you for your business!",
                new Font(Font.HELVETICA, 10, Font.ITALIC, new Color(100, 100, 100)));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30f);
        document.add(footer);
    }

    // ---- helpers ----

    private void addPlainCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, NORMAL_FONT));
        cell.setBackgroundColor(bg);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, BigDecimal value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPaddingBottom(4f);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatMoney(value), font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPaddingBottom(4f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) value = BigDecimal.ZERO;
        return "Rs. " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
