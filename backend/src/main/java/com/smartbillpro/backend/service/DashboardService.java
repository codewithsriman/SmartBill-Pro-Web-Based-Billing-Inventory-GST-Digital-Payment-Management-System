package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.dashboard.DashboardResponse;
import com.smartbillpro.backend.entity.Invoice;
import com.smartbillpro.backend.repository.CustomerRepository;
import com.smartbillpro.backend.repository.InvoiceRepository;
import com.smartbillpro.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd MMM");

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate sevenDaysAgo = today.minusDays(6);
        LocalDate twelveMonthsAgo = today.minusMonths(11).withDayOfMonth(1);
        LocalDate eightWeeksAgo = today.minusWeeks(7);

        BigDecimal todaysSales = invoiceRepository.sumGrandTotalByDate(today);
        BigDecimal monthlyRevenue = invoiceRepository.sumGrandTotalBetween(monthStart, today);

        long totalCustomers = customerRepository.countActive();
        long totalInvoices = invoiceRepository.countAll();
        long totalProducts = productRepository.countActive();

        List<DashboardResponse.ChartPoint> dailySales = buildDailySales(sevenDaysAgo, today);
        List<DashboardResponse.ChartPoint> weeklySales = buildWeeklySales(eightWeeksAgo, today);
        List<DashboardResponse.ChartPoint> monthlyRevenueChart = buildMonthlyRevenue(twelveMonthsAgo, today);

        List<DashboardResponse.RecentTransaction> recent = invoiceRepository
                .findRecent(PageRequest.of(0, 10))
                .stream()
                .map(this::toRecentTransaction)
                .toList();

        return DashboardResponse.builder()
                .todaysSales(todaysSales)
                .monthlyRevenue(monthlyRevenue)
                .totalCustomers(totalCustomers)
                .totalInvoices(totalInvoices)
                .totalProducts(totalProducts)
                .dailySales(dailySales)
                .weeklySales(weeklySales)
                .monthlyRevenueChart(monthlyRevenueChart)
                .recentTransactions(recent)
                .build();
    }

    private List<DashboardResponse.ChartPoint> buildDailySales(LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            totals.put(d, BigDecimal.ZERO);
        }
        for (Object[] row : invoiceRepository.dailySalesBetween(start, end)) {
            LocalDate day = (LocalDate) row[0];
            BigDecimal total = (BigDecimal) row[1];
            totals.put(day, total);
        }
        List<DashboardResponse.ChartPoint> points = new ArrayList<>();
        totals.forEach((day, total) -> points.add(
                DashboardResponse.ChartPoint.builder().label(day.format(DAY_LABEL)).value(total).build()));
        return points;
    }

    private List<DashboardResponse.ChartPoint> buildWeeklySales(LocalDate start, LocalDate end) {
        // Aggregate the daily figures into ISO weeks for a simple, dependency-free weekly rollup.
        Map<String, BigDecimal> weekTotals = new LinkedHashMap<>();
        for (Object[] row : invoiceRepository.dailySalesBetween(start, end)) {
            LocalDate day = (LocalDate) row[0];
            BigDecimal total = (BigDecimal) row[1];
            LocalDate weekStart = day.minusDays(day.getDayOfWeek().getValue() - 1L); // Monday of that week
            String label = weekStart.format(DAY_LABEL);
            weekTotals.merge(label, total, BigDecimal::add);
        }
        List<DashboardResponse.ChartPoint> points = new ArrayList<>();
        weekTotals.forEach((label, total) ->
                points.add(DashboardResponse.ChartPoint.builder().label(label).value(total).build()));
        return points;
    }

    private List<DashboardResponse.ChartPoint> buildMonthlyRevenue(LocalDate start, LocalDate end) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (LocalDate m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            String key = m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + m.getYear();
            totals.put(key, BigDecimal.ZERO);
        }
        for (Object[] row : invoiceRepository.monthlyRevenueBetween(start, end)) {
            String yyyymm = (String) row[0]; // e.g. "2026-06"
            BigDecimal total = (BigDecimal) row[1];
            LocalDate parsed = LocalDate.parse(yyyymm + "-01");
            String key = parsed.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + parsed.getYear();
            totals.put(key, total);
        }
        List<DashboardResponse.ChartPoint> points = new ArrayList<>();
        totals.forEach((label, total) ->
                points.add(DashboardResponse.ChartPoint.builder().label(label).value(total).build()));
        return points;
    }

    private DashboardResponse.RecentTransaction toRecentTransaction(Invoice invoice) {
        return DashboardResponse.RecentTransaction.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getCustomerNameSnap())
                .amount(invoice.getGrandTotal())
                .date(invoice.getInvoiceDate().toString())
                .paymentMethod(invoice.getPaymentMethod().name())
                .paymentStatus(invoice.getPaymentStatus().name())
                .build();
    }
}
