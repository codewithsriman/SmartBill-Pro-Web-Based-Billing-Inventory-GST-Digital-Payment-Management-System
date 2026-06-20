package com.smartbillpro.backend.util;

import com.smartbillpro.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final InvoiceRepository invoiceRepository;

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * Generates an invoice number like INV-202606-00001.
     * Uses total invoice count as a monotonically increasing suffix; for high-concurrency
     * production use, consider a dedicated DB sequence to fully eliminate race conditions.
     */
    public synchronized String generate(String prefix) {
        String yearMonth = LocalDate.now().format(YEAR_MONTH_FMT);
        long nextSeq = invoiceRepository.countAll() + 1;
        String candidate = String.format("%s-%s-%05d", prefix, yearMonth, nextSeq);

        // Guard against collisions (e.g. cancelled/deleted invoices skewing the count)
        while (invoiceRepository.findByInvoiceNumber(candidate).isPresent()) {
            nextSeq++;
            candidate = String.format("%s-%s-%05d", prefix, yearMonth, nextSeq);
        }
        return candidate;
    }
}
