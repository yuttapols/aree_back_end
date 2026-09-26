package com.roti5dao.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi.Res;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class DashboardIT extends IntegrationTest {

    /** จันทร์ 5 ต.ค. 2026 12:30 เวลาไทย — วันที่ไม่มี test อื่นใช้ */
    static final Instant DAY = Instant.parse("2026-10-05T05:30:00Z");

    private long order(String staff, long productId, int qty, boolean complete) {
        Res r = api.post("/api/v1/admin/orders", staff, Map.of("items", List.of(Map.of("productId", productId, "quantity", qty))));
        long id = r.data().get("id").asLong();
        api.post("/api/v1/admin/orders/" + id + "/payments", staff, Map.of("methodCode", "CASH"));
        if (complete) {
            for (String s : List.of("PREPARING", "READY", "COMPLETED")) {
                api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", s));
            }
        }
        return id;
    }

    @Test
    void dashboardNumbersMatchOrders() {
        clock.set(DAY);
        String staff = api.newStaff("STAFF").accessToken();
        long classic = api.productId("RT-CLASSIC"); // 30
        long milo = api.productId("DK-MILO");       // 35

        order(staff, classic, 2, true);  // 60
        order(staff, milo, 1, true);     // 35
        long cancelled = order(staff, milo, 3, false);
        api.post("/api/v1/admin/orders/" + cancelled + "/cancel", staff, Map.of("reason", "test"));

        String admin = api.adminToken();
        JsonNode s = api.get("/api/v1/admin/dashboard/summary?from=2026-10-05&to=2026-10-05", admin).data();
        assertThat(s.get("orderCount").asLong()).isEqualTo(2);
        assertThat(s.get("netSales").asDouble()).isEqualTo(95.0);
        assertThat(s.get("averagePerOrder").asDouble()).isEqualTo(47.5);
        assertThat(s.get("walkInCount").asLong()).isEqualTo(2);
        assertThat(s.get("cancelledCount").asLong()).isEqualTo(1);

        JsonNode top = api.get("/api/v1/admin/dashboard/top-products?from=2026-10-05&to=2026-10-05", admin).data();
        assertThat(top.get(0).get("productId").asLong()).isEqualTo(classic);
        assertThat(top.get(0).get("quantity").asLong()).isEqualTo(2);

        JsonNode methods = api.get("/api/v1/admin/dashboard/payment-methods?from=2026-10-05&to=2026-10-05", admin).data();
        assertThat(methods.get(0).get("methodCode").asString()).isEqualTo("CASH");
        assertThat(methods.get(0).get("amount").asDouble()).isEqualTo(95.0);

        JsonNode hourly = api.get("/api/v1/admin/dashboard/hourly?date=2026-10-05", admin).data();
        assertThat(hourly.get(0).get("hour").asInt()).isEqualTo(12);
        assertThat(hourly.get(0).get("orderCount").asLong()).isEqualTo(2);

        JsonNode trend = api.get("/api/v1/admin/dashboard/sales-trend?from=2026-10-01&to=2026-10-31&groupBy=MONTH", admin).data();
        assertThat(trend.get(0).get("period").asString()).isEqualTo("2026-10-01");

        JsonNode today = api.get("/api/v1/admin/dashboard/today", staff).data();
        assertThat(today.get("completedCount").asLong()).isEqualTo(2);
        assertThat(today.get("lastQueueNo").asInt()).isEqualTo(3);

        assertThat(api.get("/api/v1/admin/dashboard/summary?from=2026-10-05&to=2020-01-01", admin).errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(api.get("/api/v1/admin/dashboard/summary?from=2020-01-01&to=2026-10-05", admin).errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(api.get("/api/v1/admin/dashboard/sales-trend?from=2026-10-01&to=2026-10-31&groupBy=YEAR;DROP", admin).status()).isEqualTo(400);
    }
}
