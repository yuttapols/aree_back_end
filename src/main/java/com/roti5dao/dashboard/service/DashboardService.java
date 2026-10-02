package com.roti5dao.dashboard.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.time.BusinessTime;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.dashboard.dto.DashboardDtos.GroupBy;
import com.roti5dao.dashboard.dto.DashboardDtos.HourlyPoint;
import com.roti5dao.dashboard.dto.DashboardDtos.PaymentMethodShare;
import com.roti5dao.dashboard.dto.DashboardDtos.Summary;
import com.roti5dao.dashboard.dto.DashboardDtos.Today;
import com.roti5dao.dashboard.dto.DashboardDtos.TopProduct;
import com.roti5dao.dashboard.dto.DashboardDtos.TrendPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** รายงาน — query จาก view V8 (นับเฉพาะออเดอร์ COMPLETED) ผ่าน JdbcClient แบบ read-only */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final long MAX_RANGE_DAYS = 366;

    private final JdbcClient jdbc;
    private final BusinessTime time;

    public DashboardService(JdbcClient jdbc, BusinessTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public Summary summary(LocalDate from, LocalDate to) {
        checkRange(from, to);
        var sales = jdbc.sql("""
                        SELECT coalesce(sum(order_count), 0)        AS order_count,
                               coalesce(sum(gross_sales), 0)        AS gross_sales,
                               coalesce(sum(total_discount), 0)     AS total_discount,
                               coalesce(sum(net_sales), 0)          AS net_sales,
                               coalesce(sum(member_order_count), 0) AS member_order_count,
                               coalesce(sum(walk_in_count), 0)      AS walk_in_count,
                               coalesce(sum(online_count), 0)       AS online_count
                        FROM v_daily_sales WHERE sales_date BETWEEN :from AND :to
                        """)
                .param("from", from).param("to", to)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7)})
                .single();
        Timestamp start = startOf(from);
        Timestamp end = endOf(to);
        long cancelled = jdbc.sql("SELECT count(*) FROM orders WHERE status = 'CANCELLED' AND cancelled_at >= :s AND cancelled_at < :e")
                .param("s", start).param("e", end).query(Long.class).single();
        long newMembers = jdbc.sql("SELECT count(*) FROM app_user WHERE role = 'CUSTOMER' AND created_at >= :s AND created_at < :e")
                .param("s", start).param("e", end).query(Long.class).single();
        var points = jdbc.sql("""
                        SELECT coalesce(sum(points) FILTER (WHERE type = 'EARN'), 0),
                               coalesce(-sum(points) FILTER (WHERE type = 'REDEEM'), 0)
                        FROM point_transaction WHERE created_at >= :s AND created_at < :e
                        """)
                .param("s", start).param("e", end)
                .query((rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)})
                .single();

        long orderCount = (long) sales[0];
        BigDecimal net = MoneyUtils.scale((BigDecimal) sales[3]);
        BigDecimal avg = orderCount == 0 ? MoneyUtils.ZERO : net.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        return new Summary(from, to, orderCount, MoneyUtils.scale((BigDecimal) sales[1]), MoneyUtils.scale((BigDecimal) sales[2]),
                net, avg, (long) sales[4], (long) sales[5], (long) sales[6], cancelled, newMembers, points[0], points[1]);
    }

    public List<TrendPoint> salesTrend(LocalDate from, LocalDate to, GroupBy groupBy) {
        checkRange(from, to);
        // groupBy เป็น enum → ค่าที่ส่งเข้า date_trunc มาจาก whitelist เท่านั้น (และยัง bind เป็น parameter)
        return jdbc.sql("""
                        SELECT date_trunc(:unit, sales_date)::date AS period,
                               sum(order_count) AS order_count, sum(net_sales) AS net_sales
                        FROM v_daily_sales WHERE sales_date BETWEEN :from AND :to
                        GROUP BY 1 ORDER BY 1
                        """)
                .param("unit", groupBy.name().toLowerCase(Locale.ROOT))
                .param("from", from).param("to", to)
                .query((rs, n) -> new TrendPoint(rs.getObject(1, LocalDate.class), rs.getLong(2), MoneyUtils.scale(rs.getBigDecimal(3))))
                .list();
    }

    public List<TopProduct> topProducts(LocalDate from, LocalDate to, int limit) {
        checkRange(from, to);
        return jdbc.sql("""
                        SELECT product_id, max(product_name), sum(quantity) AS qty, sum(amount) AS amount
                        FROM v_product_sales WHERE sales_date BETWEEN :from AND :to
                        GROUP BY product_id ORDER BY qty DESC, amount DESC LIMIT :limit
                        """)
                .param("from", from).param("to", to).param("limit", Math.clamp(limit, 1, 50))
                .query((rs, n) -> new TopProduct(rs.getLong(1), rs.getString(2), rs.getLong(3), MoneyUtils.scale(rs.getBigDecimal(4))))
                .list();
    }

    public List<PaymentMethodShare> paymentMethods(LocalDate from, LocalDate to) {
        checkRange(from, to);
        return jdbc.sql("""
                        SELECT method_code, max(method_name), sum(payment_count), sum(amount)
                        FROM v_payment_method_sales WHERE sales_date BETWEEN :from AND :to
                        GROUP BY method_code ORDER BY 4 DESC
                        """)
                .param("from", from).param("to", to)
                .query((rs, n) -> new PaymentMethodShare(rs.getString(1), rs.getString(2), rs.getLong(3), MoneyUtils.scale(rs.getBigDecimal(4))))
                .list();
    }

    public List<HourlyPoint> hourly(LocalDate date) {
        return jdbc.sql("""
                        SELECT extract(hour FROM completed_at AT TIME ZONE :tz)::int AS h,
                               count(*), sum(total_amount)
                        FROM orders
                        WHERE status = 'COMPLETED' AND completed_at >= :s AND completed_at < :e
                        GROUP BY 1 ORDER BY 1
                        """)
                .param("tz", time.zone().getId())
                .param("s", startOf(date)).param("e", endOf(date))
                .query((rs, n) -> new HourlyPoint(rs.getInt(1), rs.getLong(2), MoneyUtils.scale(rs.getBigDecimal(3))))
                .list();
    }

    public Today today() {
        LocalDate date = time.today();
        Timestamp s = startOf(date);
        Timestamp e = endOf(date);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.sql("SELECT status, count(*) FROM orders WHERE created_at >= :s AND created_at < :e GROUP BY status ORDER BY status")
                .param("s", s).param("e", e)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getLong(2)))
                .list().forEach(en -> byStatus.put(en.getKey(), en.getValue()));
        var completed = jdbc.sql("""
                        SELECT count(*), coalesce(sum(total_amount), 0) FROM orders
                        WHERE status = 'COMPLETED' AND completed_at >= :s AND completed_at < :e
                        """)
                .param("s", s).param("e", e)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getBigDecimal(2)})
                .single();
        long pending = jdbc.sql("SELECT count(*) FROM payment WHERE status = 'PENDING'").query(Long.class).single();
        int lastQueue = jdbc.sql("SELECT coalesce(max(last_value), 0) FROM daily_counter WHERE counter_date = :d AND counter_name = 'QUEUE'")
                .param("d", date).query(Integer.class).single();
        return new Today(date, byStatus, (long) completed[0], MoneyUtils.scale((BigDecimal) completed[1]), pending, lastQueue);
    }

    private Timestamp startOf(LocalDate date) {
        return Timestamp.from(time.startOfDay(date));
    }

    private Timestamp endOf(LocalDate date) {
        return Timestamp.from(time.endOfDay(date));
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ช่วงวันที่ไม่ถูกต้อง");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ช่วงวันที่ต้องไม่เกิน " + MAX_RANGE_DAYS + " วัน");
        }
    }
}
