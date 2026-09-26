package com.roti5dao.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public enum GroupBy {
        DAY, WEEK, MONTH
    }

    public record Summary(LocalDate from, LocalDate to, long orderCount, BigDecimal grossSales, BigDecimal totalDiscount,
                          BigDecimal netSales, BigDecimal averagePerOrder, long memberOrderCount, long walkInCount,
                          long onlineCount, long cancelledCount, long newMembers, long pointsIssued, long pointsRedeemed) {
    }

    public record TrendPoint(LocalDate period, long orderCount, BigDecimal netSales) {
    }

    public record TopProduct(Long productId, String productName, long quantity, BigDecimal amount) {
    }

    public record PaymentMethodShare(String methodCode, String methodName, long paymentCount, BigDecimal amount) {
    }

    public record HourlyPoint(int hour, long orderCount, BigDecimal netSales) {
    }

    public record Today(LocalDate date, Map<String, Long> ordersByStatus, long completedCount, BigDecimal netSales,
                        long pendingPayments, int lastQueueNo) {
    }
}
