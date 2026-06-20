package com.smartbillpro.backend.repository;

import com.smartbillpro.backend.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    @Query("SELECT i FROM Invoice i WHERE i.status != 'CANCELLED' AND " +
           "(LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(i.customerNameSnap) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR i.customerPhoneSnap LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY i.createdAt DESC")
    Page<Invoice> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE i.status != 'CANCELLED' ORDER BY i.createdAt DESC")
    Page<Invoice> findRecent(Pageable pageable);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.status = 'FINALIZED' AND i.invoiceDate = :date")
    BigDecimal sumGrandTotalByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.status = 'FINALIZED' " +
           "AND i.invoiceDate BETWEEN :start AND :end")
    BigDecimal sumGrandTotalBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status != 'CANCELLED'")
    long countAll();

    @Query("SELECT i.invoiceDate as day, COALESCE(SUM(i.grandTotal),0) as total FROM Invoice i " +
           "WHERE i.status = 'FINALIZED' AND i.invoiceDate BETWEEN :start AND :end GROUP BY i.invoiceDate ORDER BY i.invoiceDate")
    List<Object[]> dailySalesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT FUNCTION('DATE_FORMAT', i.invoiceDate, '%Y-%m') as month, COALESCE(SUM(i.grandTotal),0) as total " +
           "FROM Invoice i WHERE i.status = 'FINALIZED' AND i.invoiceDate BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE_FORMAT', i.invoiceDate, '%Y-%m') ORDER BY month")
    List<Object[]> monthlyRevenueBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Invoice> findByInvoiceDateBetweenAndStatus(LocalDate start, LocalDate end, Invoice.InvoiceStatus status);

    List<Invoice> findByCustomerIdAndStatus(Long customerId, Invoice.InvoiceStatus status);
}
