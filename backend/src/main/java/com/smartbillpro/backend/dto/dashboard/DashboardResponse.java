package com.smartbillpro.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class DashboardResponse {

    private BigDecimal todaysSales;
    private BigDecimal monthlyRevenue;
    private long totalCustomers;
    private long totalInvoices;
    private long totalProducts;

    private List<ChartPoint> dailySales;      // last 7 days
    private List<ChartPoint> weeklySales;     // last ~8 weeks, aggregated
    private List<ChartPoint> monthlyRevenueChart; // last 12 months

    private List<RecentTransaction> recentTransactions;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    public static class ChartPoint {
        private String label;
        private BigDecimal value;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    public static class RecentTransaction {
        private String invoiceNumber;
        private String customerName;
        private BigDecimal amount;
        private String date;
        private String paymentMethod;
        private String paymentStatus;
    }
}
