package com.roti5dao.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.point.service.PointService;
import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi.Res;
import com.roti5dao.support.TestApi.Session;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/** Phase 4: แต้ม + โปรโมชั่น */
class LoyaltyIT extends IntegrationTest {

    @Autowired
    PointService pointService;

    private static Map<String, Object> item(long productId, int qty) {
        return Map.of("productId", productId, "quantity", qty);
    }

    private String phoneOf(Session s) {
        return api.get("/api/v1/me", s.accessToken()).data().get("phone").asString();
    }

    private int balance(Session s) {
        return api.get("/api/v1/me/points", s.accessToken()).data().get("balance").asInt();
    }

    /** POS: เปิดบิลสมาชิก → จ่ายเงินสด → ทำจนเสร็จ */
    private JsonNode completeWalkIn(String staff, String customerPhone, Map<String, Object> extra, Map<String, Object> item) {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("items", List.of(item));
        if (customerPhone != null) {
            body.put("customerPhone", customerPhone);
        }
        Res created = api.post("/api/v1/admin/orders", staff, body);
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        long id = created.data().get("id").asLong();
        if (created.data().get("status").asString().equals("PENDING_PAYMENT")) {
            api.post("/api/v1/admin/orders/" + id + "/payments", staff, Map.of("methodCode", "CASH"));
        }
        for (String s : List.of("PREPARING", "READY", "COMPLETED")) {
            api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", s));
        }
        return api.get("/api/v1/admin/orders/" + id, staff).data();
    }

    private Map<String, Object> promo(String code, String type, double value) {
        Map<String, Object> p = new HashMap<>();
        if (code != null) {
            p.put("code", code);
        }
        p.put("name", "โปร " + (code == null ? type : code));
        p.put("type", type);
        p.put("discountValue", value);
        p.put("minOrderAmount", 0);
        p.put("scope", "ORDER");
        p.put("memberOnly", false);
        p.put("channel", "ALL");
        p.put("startAt", START.minus(Duration.ofDays(1)).toString());
        p.put("endAt", START.plus(Duration.ofDays(7)).toString());
        p.put("showOnLanding", true);
        p.put("priority", 0);
        p.put("active", true);
        return p;
    }

    private long createPromo(Map<String, Object> body) {
        Res r = api.post("/api/v1/admin/promotions", api.adminToken(), body);
        assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        return r.data().get("id").asLong();
    }

    private Res quote(String token, long productId, int qty, String code, Integer redeem) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item(productId, qty)));
        if (code != null) {
            body.put("promoCode", code);
        }
        if (redeem != null) {
            body.put("redeemPoints", redeem);
        }
        return api.post("/api/v1/public/orders/quote", token, body);
    }

    // ------------------------------------------------------------------ points

    @Test
    void memberEarnsPointsWhenOrderCompleted() {
        Session c = api.newCustomer();
        String staff = api.newStaff("STAFF").accessToken();
        long beef = api.productId("MT-BEEF"); // 80

        assertThat(quote(c.accessToken(), beef, 5, null, null).data().get("pointsToEarn").asInt()).isEqualTo(16);
        JsonNode order = completeWalkIn(staff, phoneOf(c), Map.of(), item(beef, 5)); // 400 บาท → 16 แต้ม
        assertThat(order.get("pointsEarned").asInt()).isEqualTo(16);
        assertThat(balance(c)).isEqualTo(16);
        assertThat(pointService.isLedgerConsistent(c.userId())).isTrue();

        // guest ไม่ได้แต้ม
        JsonNode guest = completeWalkIn(staff, null, Map.of(), item(beef, 5));
        assertThat(guest.get("pointsEarned").asInt()).isZero();
    }

    @Test
    void redeemRulesAndCancelRestoresPoints() {
        Session c = api.newCustomer();
        long beef = api.productId("MT-BEEF");
        long milo = api.productId("DK-MILO"); // 35

        assertThat(api.post("/api/v1/admin/customers/" + c.userId() + "/points/adjust", api.adminToken(),
                Map.of("points", 300, "remark", "ของขวัญเปิดร้าน")).status()).isEqualTo(200);
        assertThat(balance(c)).isEqualTo(300);

        assertThat(quote(c.accessToken(), beef, 5, null, 50).errorCode()).isEqualTo("POINT_BELOW_MIN");
        assertThat(quote(c.accessToken(), beef, 5, null, 100_000).errorCode()).isEqualTo("POINT_INSUFFICIENT");
        assertThat(quote(c.accessToken(), milo, 1, null, 300).errorCode()).isEqualTo("POINT_EXCEED_LIMIT"); // 30฿ > 50% ของ 35฿
        assertThat(quote(null, beef, 5, null, 150).errorCode()).isEqualTo("POINT_INSUFFICIENT"); // guest แลกไม่ได้

        Res q = quote(c.accessToken(), beef, 5, null, 155);
        assertThat(q.data().get("pointDiscount").asDouble()).isEqualTo(15.0);
        assertThat(q.data().get("pointsRedeemed").asInt()).isEqualTo(150); // ใช้เท่าที่แลกเป็นบาทได้
        assertThat(q.data().get("total").asDouble()).isEqualTo(385.0);

        Res order = api.post("/api/v1/public/orders", c.accessToken(), Map.of("items", List.of(item(beef, 5)), "redeemPoints", 150));
        assertThat(order.status()).isEqualTo(201);
        assertThat(order.data().get("totalAmount").asDouble()).isEqualTo(385.0);
        assertThat(balance(c)).isEqualTo(150);

        String staff = api.newStaff("STAFF").accessToken();
        api.post("/api/v1/admin/orders/" + order.data().get("id").asLong() + "/cancel", staff, Map.of("reason", "test"));
        assertThat(balance(c)).isEqualTo(300);
        assertThat(pointService.isLedgerConsistent(c.userId())).isTrue();

        JsonNode txs = api.get("/api/v1/me/points/transactions", c.accessToken()).data().get("items");
        assertThat(txs.get(0).get("type").asString()).isEqualTo("REVERSE");
    }

    @Test
    void pointsExpireFifo() {
        Session c = api.newCustomer();
        String phone = phoneOf(c);
        api.post("/api/v1/admin/customers/" + c.userId() + "/points/adjust", api.adminToken(), Map.of("points", 100, "remark", "lot1"));
        clock.advance(Duration.ofDays(200));
        api.post("/api/v1/admin/customers/" + c.userId() + "/points/adjust", api.adminToken(), Map.of("points", 120, "remark", "lot2"));
        clock.advance(Duration.ofDays(170)); // lot1 หมดอายุ (365 วัน), lot2 ยังไม่หมด
        c = api.loginOk(phone, "Passw0rd!");  // session เดิมหมดอายุแล้ว
        String admin = api.adminToken();

        assertThat(pointService.customersWithExpiredLots(1000)).contains(c.userId());
        assertThat(pointService.expireForCustomer(c.userId())).isEqualTo(100);
        assertThat(balance(c)).isEqualTo(120);
        assertThat(pointService.isLedgerConsistent(c.userId())).isTrue();

        // ปรับลดเกินยอดไม่ได้
        assertThat(api.post("/api/v1/admin/customers/" + c.userId() + "/points/adjust", admin,
                Map.of("points", -500, "remark", "x")).errorCode()).isEqualTo("POINT_INSUFFICIENT");
        assertThat(api.post("/api/v1/admin/customers/" + c.userId() + "/points/adjust", admin,
                Map.of("points", -20, "remark", "แก้ไข")).status()).isEqualTo(200);
        assertThat(balance(c)).isEqualTo(100);
        assertThat(pointService.isLedgerConsistent(c.userId())).isTrue();
    }

    // ------------------------------------------------------------------ promotions

    @Test
    void promoCodePercentWithCapAndExpiry() {
        var p = promo("SAVE10", "PERCENT", 10);
        p.put("maxDiscount", 20);
        createPromo(p);
        long beef = api.productId("MT-BEEF");

        assertThat(quote(null, beef, 1, "save10", null).data().get("promotionDiscount").asDouble()).isEqualTo(8.0);
        assertThat(quote(null, beef, 5, "SAVE10", null).data().get("promotionDiscount").asDouble()).isEqualTo(20.0); // เพดาน
        assertThat(quote(null, beef, 1, "NOPE", null).errorCode()).isEqualTo("PROMOTION_NOT_FOUND");

        clock.advance(Duration.ofDays(8));
        assertThat(quote(null, beef, 1, "SAVE10", null).errorCode()).isEqualTo("PROMOTION_EXPIRED");
    }

    @Test
    void memberOnlyPromoRejectsGuest() {
        var p = promo("MEMBER20", "FIXED_AMOUNT", 20);
        p.put("memberOnly", true);
        p.put("minOrderAmount", 100);
        createPromo(p);
        long beef = api.productId("MT-BEEF");
        assertThat(quote(null, beef, 2, "MEMBER20", null).errorCode()).isEqualTo("PROMOTION_NOT_ELIGIBLE");
        Session c = api.newCustomer();
        assertThat(quote(c.accessToken(), beef, 1, "MEMBER20", null).errorCode()).isEqualTo("PROMOTION_NOT_ELIGIBLE"); // ยอดไม่ถึง
        assertThat(quote(c.accessToken(), beef, 2, "MEMBER20", null).data().get("total").asDouble()).isEqualTo(140.0);
    }

    @Test
    void usageLimitHoldsUnderConcurrency() throws Exception {
        var p = promo("LIMIT2", "FIXED_AMOUNT", 5);
        p.put("usageLimit", 2);
        long promoId = createPromo(p);
        long milo = api.productId("DK-MILO");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        int ok = 0;
        int limited = 0;
        try {
            List<Callable<Res>> tasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String phone = "08111000" + (10 + i);
                tasks.add(() -> api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(milo, 1)),
                        "promoCode", "LIMIT2", "guestName", "g", "guestPhone", phone)));
            }
            List<Long> created = new ArrayList<>();
            for (Future<Res> f : pool.invokeAll(tasks)) {
                Res r = f.get();
                if (r.status() == 201) {
                    ok++;
                    created.add(r.data().get("id").asLong());
                } else if ("PROMOTION_LIMIT_REACHED".equals(r.errorCode())) {
                    limited++;
                }
            }
            assertThat(ok).isEqualTo(2);
            assertThat(limited).isEqualTo(4);
            assertThat(api.get("/api/v1/admin/promotions/" + promoId, api.adminToken()).data().get("usedCount").asInt()).isEqualTo(2);

            // ยกเลิก → คืนโควต้า
            api.post("/api/v1/admin/orders/" + created.get(0) + "/cancel", api.adminToken(), Map.of("reason", "test"));
            assertThat(api.get("/api/v1/admin/promotions/" + promoId, api.adminToken()).data().get("usedCount").asInt()).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void perCustomerLimit() {
        var p = promo("ONCE", "FIXED_AMOUNT", 5);
        p.put("usagePerCustomer", 1);
        createPromo(p);
        Session c = api.newCustomer();
        long milo = api.productId("DK-MILO");
        assertThat(api.post("/api/v1/public/orders", c.accessToken(),
                Map.of("items", List.of(item(milo, 1)), "promoCode", "ONCE")).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/public/orders", c.accessToken(),
                Map.of("items", List.of(item(milo, 1)), "promoCode", "ONCE")).errorCode()).isEqualTo("PROMOTION_NOT_ELIGIBLE");
    }

    @Test
    void buyTwoGetOneOnTargetProduct() {
        long cat = api.post("/api/v1/admin/categories", api.adminToken(),
                Map.of("name", "bxgy", "slug", "test-bxgy", "sortOrder", 99, "active", true)).data().get("id").asLong();
        long prod = api.post("/api/v1/admin/products", api.adminToken(), Map.of("categoryId", cat, "code", "TST-BXGY",
                "name", "โรตีทดสอบ", "price", 20, "available", true, "recommended", false, "active", true, "sortOrder", 1))
                .data().get("id").asLong();
        var p = promo("B2G1", "BUY_X_GET_Y", 0);
        p.put("buyQty", 2);
        p.put("getQty", 1);
        p.put("scope", "PRODUCT");
        p.put("productIds", List.of(prod));
        createPromo(p);

        Res q = quote(null, prod, 7, "B2G1", null); // 7 ชิ้น → ฟรี 2
        assertThat(q.data().get("promotionDiscount").asDouble()).isEqualTo(40.0);
        assertThat(q.data().get("items").get(0).get("freeQuantity").asInt()).isEqualTo(2);
        // สินค้าอื่นไม่เข้าเงื่อนไข
        assertThat(quote(null, api.productId("DK-MILO"), 3, "B2G1", null).errorCode()).isEqualTo("PROMOTION_NOT_ELIGIBLE");
    }

    @Test
    void autoApplyPicksBestDiscount() {
        long cat = api.post("/api/v1/admin/categories", api.adminToken(),
                Map.of("name", "auto", "slug", "test-auto", "sortOrder", 99, "active", true)).data().get("id").asLong();
        long prod = api.post("/api/v1/admin/products", api.adminToken(), Map.of("categoryId", cat, "code", "TST-AUTO",
                "name", "auto", "price", 100, "available", true, "recommended", false, "active", true, "sortOrder", 1))
                .data().get("id").asLong();
        var small = promo(null, "FIXED_AMOUNT", 5);
        small.put("scope", "PRODUCT");
        small.put("productIds", List.of(prod));
        var big = promo(null, "PERCENT", 30);
        big.put("scope", "PRODUCT");
        big.put("productIds", List.of(prod));
        long a = createPromo(small);
        long b = createPromo(big);
        try {
            Res q = quote(null, prod, 1, null, null);
            assertThat(q.data().get("promotionDiscount").asDouble()).isEqualTo(30.0);
            assertThat(q.data().get("appliedPromotions").get(0).get("promotionId").asLong()).isEqualTo(b);
            // โปร auto ไม่กระทบสินค้าอื่น
            assertThat(quote(null, api.productId("DK-MILO"), 1, null, null).data().get("promotionDiscount").asDouble()).isZero();
        } finally {
            api.call(HttpMethod.DELETE, "/api/v1/admin/promotions/" + a, api.adminToken(), null);
            api.call(HttpMethod.DELETE, "/api/v1/admin/promotions/" + b, api.adminToken(), null);
        }
    }

    @Test
    void wednesdayDoublePoints() {
        var p = promo("WEDX2", "POINT_MULTIPLIER", 2);
        p.put("daysOfWeek", List.of(3));
        createPromo(p);
        Session c = api.newCustomer();
        String phone = phoneOf(c);
        long beef = api.productId("MT-BEEF");

        assertThat(quote(c.accessToken(), beef, 5, "WEDX2", null).data().get("pointsToEarn").asInt()).isEqualTo(32);
        assertThat(quote(null, beef, 5, "WEDX2", null).errorCode()).isEqualTo("PROMOTION_NOT_ELIGIBLE"); // guest

        String staff = api.newStaff("STAFF").accessToken();
        JsonNode order = completeWalkIn(staff, phone, Map.of("promoCode", "WEDX2"), item(beef, 5));
        assertThat(order.get("pointsEarned").asInt()).isEqualTo(32);
        assertThat(balance(c)).isEqualTo(32);

        clock.advance(Duration.ofDays(1)); // พฤหัส
        c = api.loginOk(phone, "Passw0rd!");
        assertThat(quote(c.accessToken(), beef, 5, "WEDX2", null).errorCode()).isEqualTo("PROMOTION_NOT_ELIGIBLE");
    }

    @Test
    void promotionValidationAndPublicListing() {
        var bad = promo("BAD1", "PERCENT", 150);
        assertThat(api.post("/api/v1/admin/promotions", api.adminToken(), bad).errorCode()).isEqualTo("VALIDATION_ERROR");
        var badScope = promo("BAD2", "FIXED_AMOUNT", 10);
        badScope.put("scope", "PRODUCT");
        assertThat(api.post("/api/v1/admin/promotions", api.adminToken(), badScope).errorCode()).isEqualTo("VALIDATION_ERROR");
        createPromo(promo("DUPX", "FIXED_AMOUNT", 1));
        assertThat(api.post("/api/v1/admin/promotions", api.adminToken(), promo("dupx", "FIXED_AMOUNT", 1)).errorCode())
                .isEqualTo("DUPLICATE_VALUE");

        assertThat(api.get("/api/v1/public/promotions", null).data().size()).isGreaterThan(0);
        assertThat(api.get("/api/v1/admin/promotions", api.newStaff("STAFF").accessToken()).status()).isEqualTo(403);
    }
}
