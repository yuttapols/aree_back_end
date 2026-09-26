package com.roti5dao.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi.Res;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

class OrderPaymentIT extends IntegrationTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    private long eggOptionId(long productId) {
        for (JsonNode g : api.get("/api/v1/public/products/" + productId, null).data().get("optionGroups")) {
            for (JsonNode i : g.get("items")) {
                if ("ไข่".equals(i.get("name").asString())) {
                    return i.get("id").asLong();
                }
            }
        }
        throw new IllegalStateException("egg option not found");
    }

    private static Map<String, Object> item(long productId, int qty, Long... options) {
        return Map.of("productId", productId, "quantity", qty, "optionItemIds", List.of(options));
    }

    private Res walkIn(String staffToken, Map<String, Object> extra, Map<String, Object>... items) {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("items", List.of(items));
        return api.post("/api/v1/admin/orders", staffToken, body);
    }

    @Test
    void walkInGuestPaysCashAndGetsChange() {
        var staff = api.newStaff("STAFF");
        long classic = api.productId("RT-CLASSIC"); // 30 บาท
        long egg = eggOptionId(classic);          // +10

        Res order = walkIn(staff.accessToken(), Map.of(), item(classic, 2, egg));
        assertThat(order.status()).isEqualTo(201);
        JsonNode o = order.data();
        assertThat(o.get("customer").isNull()).isTrue();
        assertThat(o.get("totalAmount").asDouble()).isEqualTo(80.0);
        assertThat(o.get("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(o.get("orderNo").asString()).matches("R5D-260923-\\d{4}");
        long id = o.get("id").asLong();

        Res paid = api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(),
                Map.of("methodCode", "CASH", "cashReceived", 100));
        assertThat(paid.status()).isEqualTo(201);
        assertThat(paid.data().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(paid.data().get("payments").get(0).get("changeAmount").asDouble()).isEqualTo(20.0);

        assertThat(boardContains(staff.accessToken(), id)).isTrue();
        for (String s : List.of("PREPARING", "READY", "COMPLETED")) {
            assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff.accessToken(), Map.of("status", s))
                    .data().get("status").asString()).isEqualTo(s);
        }
        assertThat(boardContains(staff.accessToken(), id)).isFalse();

        var receipt = api.get("/api/v1/admin/orders/" + id + "/receipt", staff.accessToken());
        assertThat(receipt.data().get("shopName").asString()).isEqualTo("ร้านโรตี 5 ดาว");
    }

    @Test
    void invalidStatusTransitionsRejected() {
        var staff = api.newStaff("STAFF");
        long id = walkIn(staff.accessToken(), Map.of(), item(api.productId("DK-MILO"), 1)).data().get("id").asLong();

        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff.accessToken(), Map.of("status", "READY"))
                .errorCode()).isEqualTo("ORDER_INVALID_STATUS");
        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff.accessToken(), Map.of("status", "CONFIRMED"))
                .errorCode()).isEqualTo("ORDER_INVALID_STATUS");
        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff.accessToken(), Map.of("status", "CANCELLED"))
                .errorCode()).isEqualTo("ORDER_INVALID_STATUS");

        assertThat(api.post("/api/v1/admin/orders/" + id + "/cancel", staff.accessToken(), Map.of("reason", "ลูกค้าเปลี่ยนใจ"))
                .data().get("status").asString()).isEqualTo("CANCELLED");
        // ยกเลิกแล้วจ่ายเงินไม่ได้
        assertThat(api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(), Map.of("methodCode", "CASH"))
                .errorCode()).isEqualTo("ORDER_INVALID_STATUS");
    }

    @Test
    void onlineGuestUploadsSlipAndStaffApproves() {
        long banana = api.productId("RT-BANANA"); // 45
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(banana, 2)),
                "guestName", "คุณเอ", "guestPhone", "081-234-5678"));
        assertThat(created.status()).isEqualTo(201);
        String token = created.data().get("trackingToken").asString();
        assertThat(created.data().get("totalAmount").asDouble()).isEqualTo(90.0);

        Res track = api.get("/api/v1/public/orders/track/" + token, null);
        assertThat(track.data().get("maskedPhone").asString()).isEqualTo("081-xxx-5678");
        assertThat(track.data().has("trackingToken")).isFalse();

        // ออนไลน์เลือกเงินสดไม่ได้, ต้องแนบสลิป
        assertThat(slip(token, "CASH", JPEG).errorCode()).isEqualTo("PAYMENT_METHOD_UNAVAILABLE");
        assertThat(slip(token, "PROMPTPAY", null).errorCode()).isEqualTo("SLIP_REQUIRED");

        Res submitted = slip(token, "PROMPTPAY", JPEG);
        assertThat(submitted.status()).isEqualTo(201);
        assertThat(submitted.data().get("payments").get(0).get("status").asString()).isEqualTo("PENDING");

        var staff = api.newStaff("STAFF");
        long paymentId = findPending(staff.accessToken(), created.data().get("orderNo").asString());

        // สลิปดูได้เฉพาะพนักงาน
        var slipRes = api.get("/api/v1/admin/payments/" + paymentId + "/slip", staff.accessToken()).raw().getResponse();
        assertThat(slipRes.getStatus()).isEqualTo(200);
        assertThat(slipRes.getContentType()).isEqualTo("image/jpeg");
        assertThat(slipRes.getHeader("Cache-Control")).contains("no-store");
        assertThat(api.get("/api/v1/admin/payments/" + paymentId + "/slip", api.newCustomer().accessToken()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/admin/payments/" + paymentId + "/slip", null).status()).isEqualTo(401);

        Res approved = api.patch("/api/v1/admin/payments/" + paymentId + "/verify", staff.accessToken(), Map.of("approve", true));
        assertThat(approved.data().get("status").asString()).isEqualTo("PAID");
        assertThat(api.get("/api/v1/public/orders/track/" + token, null).data().get("status").asString()).isEqualTo("CONFIRMED");

        // ตรวจซ้ำไม่ได้
        assertThat(api.patch("/api/v1/admin/payments/" + paymentId + "/verify", staff.accessToken(), Map.of("approve", true))
                .errorCode()).isEqualTo("INVALID_OPERATION");
    }

    @Test
    void rejectedSlipKeepsOrderPending() {
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(api.productId("DK-THAITEA"), 1)),
                "guestName", "คุณบี", "guestPhone", "0891112222"));
        String token = created.data().get("trackingToken").asString();
        slip(token, "TRANSFER", JPEG);
        var staff = api.newStaff("STAFF");
        long paymentId = findPending(staff.accessToken(), created.data().get("orderNo").asString());

        assertThat(api.patch("/api/v1/admin/payments/" + paymentId + "/verify", staff.accessToken(), Map.of("approve", false))
                .errorCode()).isEqualTo("VALIDATION_ERROR"); // ต้องมีเหตุผล
        Res rejected = api.patch("/api/v1/admin/payments/" + paymentId + "/verify", staff.accessToken(),
                Map.of("approve", false, "rejectReason", "ยอดไม่ตรง"));
        assertThat(rejected.data().get("status").asString()).isEqualTo("REJECTED");
        JsonNode track = api.get("/api/v1/public/orders/track/" + token, null).data();
        assertThat(track.get("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(track.get("payments").get(0).get("rejectReason").asString()).isEqualTo("ยอดไม่ตรง");
    }

    @Test
    void slipSpamLimited() {
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(api.productId("DK-THAITEA"), 1)),
                "guestName", "spam", "guestPhone", "0891113333"));
        String token = created.data().get("trackingToken").asString();
        for (int i = 0; i < 3; i++) {
            assertThat(slip(token, "TRANSFER", JPEG).status()).isEqualTo(201);
        }
        assertThat(slip(token, "TRANSFER", JPEG).errorCode()).isEqualTo("TOO_MANY_PENDING_PAYMENTS");
    }

    @Test
    void mixedPaymentCashPlusCard() {
        var staff = api.newStaff("STAFF");
        long id = walkIn(staff.accessToken(), Map.of(), item(api.productId("MT-BEEF"), 1)).data().get("id").asLong(); // 80

        assertThat(api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(),
                Map.of("methodCode", "CASH", "amount", 100)).errorCode()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        assertThat(api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(),
                Map.of("methodCode", "CASH", "amount", 30, "cashReceived", 20)).errorCode()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");

        Res part = api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(),
                Map.of("methodCode", "CASH", "amount", 30, "cashReceived", 50));
        assertThat(part.data().get("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(part.data().get("remainingAmount").asDouble()).isEqualTo(50.0);

        assertThat(api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(),
                Map.of("methodCode", "CARD")).errorCode()).isEqualTo("REFERENCE_REQUIRED");
        Res rest = api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(),
                Map.of("methodCode", "CARD", "referenceNo", "EDC-001"));
        assertThat(rest.data().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(rest.data().get("paidAmount").asDouble()).isEqualTo(80.0);
    }

    @Test
    void serverComputesPriceAndValidatesOptions() {
        long classic = api.productId("RT-CLASSIC");
        long curry = api.productId("RT-CURRY");
        long egg = eggOptionId(classic);

        // ราคาที่ client แอบส่งมาถูกละเลย
        Map<String, Object> tampered = new HashMap<>(item(classic, 1));
        tampered.put("price", 0.01);
        Res quote = api.post("/api/v1/public/orders/quote", null, Map.of("items", List.of(tampered)));
        assertThat(quote.data().get("total").asDouble()).isEqualTo(30.0);

        // ตัวเลือกของสินค้าอื่น
        assertThat(api.post("/api/v1/public/orders/quote", null, Map.of("items", List.of(item(curry, 1, egg))))
                .errorCode()).isEqualTo("OPTION_INVALID");
        assertThat(api.post("/api/v1/public/orders/quote", null, Map.of("items", List.of(item(classic, 1, egg, egg))))
                .errorCode()).isEqualTo("OPTION_INVALID");
        assertThat(api.post("/api/v1/public/orders/quote", null, Map.of("items", List.of(item(classic, 0))))
                .status()).isEqualTo(400);
        assertThat(api.post("/api/v1/public/orders/quote", null, Map.of("items", List.of(item(999_999, 1))))
                .errorCode()).isEqualTo("PRODUCT_UNAVAILABLE");
        assertThat(api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(classic, 1))))
                .errorCode()).isEqualTo("VALIDATION_ERROR"); // guest ไม่มีชื่อ/เบอร์
    }

    @Test
    void walkInMemberByPhone() {
        var customer = api.newCustomer();
        String phone = api.get("/api/v1/me", customer.accessToken()).data().get("phone").asString();
        var staff = api.newStaff("STAFF");

        Res lookup = api.get("/api/v1/admin/customers/lookup?phone=" + phone, staff.accessToken());
        assertThat(lookup.data().get("userId").asLong()).isEqualTo(customer.userId());

        Res order = walkIn(staff.accessToken(), Map.of("customerPhone", phone), item(api.productId("DK-MILO"), 1));
        assertThat(order.data().get("customer").get("id").asLong()).isEqualTo(customer.userId());
        assertThat(walkIn(staff.accessToken(), Map.of("customerPhone", "0870000000"), item(api.productId("DK-MILO"), 1))
                .errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void quickRegisterGivesTemporaryPassword() {
        var staff = api.newStaff("STAFF");
        String phone = com.roti5dao.support.TestApi.uniquePhone();
        Res r = api.post("/api/v1/admin/customers/quick-register", staff.accessToken(), Map.of("nickname", "ป้า", "phone", phone));
        assertThat(r.status()).isEqualTo(201);
        String temp = r.data().get("temporaryPassword").asString();
        var s = api.loginOk(phone, temp);
        assertThat(api.get("/api/v1/me/orders", s.accessToken()).errorCode()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void cancelConfirmedOrderRefundsPayments() {
        var staff = api.newStaff("STAFF");
        long id = walkIn(staff.accessToken(), Map.of(), item(api.productId("DK-MILO"), 1)).data().get("id").asLong();
        api.post("/api/v1/admin/orders/" + id + "/payments", staff.accessToken(), Map.of("methodCode", "CASH"));
        Res cancelled = api.post("/api/v1/admin/orders/" + id + "/cancel", staff.accessToken(), Map.of("reason", "ของหมด"));
        assertThat(cancelled.data().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.data().get("payments").get(0).get("status").asString()).isEqualTo("REFUNDED");
    }

    @Test
    void onlineOrderingCanBeClosed() {
        String admin = api.adminToken();
        api.put("/api/v1/admin/settings", admin, Map.of("values", Map.of("shop.accept_online_order", "false")));
        try {
            Res r = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(api.productId("DK-MILO"), 1)),
                    "guestName", "x", "guestPhone", "0812345678"));
            assertThat(r.errorCode()).isEqualTo("ONLINE_ORDER_CLOSED");
        } finally {
            api.put("/api/v1/admin/settings", admin, Map.of("values", Map.of("shop.accept_online_order", "true")));
        }
        assertThat(api.put("/api/v1/admin/settings", admin, Map.of("values", Map.of("unknown.key", "1"))).errorCode())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(api.put("/api/v1/admin/settings", admin, Map.of("values", Map.of("point.expire_days", "abc"))).errorCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void orderNumbersUniqueUnderConcurrency() throws Exception {
        var staff = api.newStaff("STAFF");
        long milo = api.productId("DK-MILO");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Res>> tasks = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                tasks.add(() -> walkIn(staff.accessToken(), Map.of(), item(milo, 1)));
            }
            Set<String> orderNos = new HashSet<>();
            Set<Integer> queues = new HashSet<>();
            for (Future<Res> f : pool.invokeAll(tasks)) {
                Res r = f.get();
                assertThat(r.status()).isEqualTo(201);
                orderNos.add(r.data().get("orderNo").asString());
                queues.add(r.data().get("queueNo").asInt());
            }
            assertThat(orderNos).hasSize(24);
            assertThat(queues).hasSize(24);
        } finally {
            pool.shutdown();
        }
    }

    private Res slip(String token, String method, byte[] bytes) {
        var b = MockMvcRequestBuilders.multipart("/api/v1/public/orders/track/" + token + "/payments").param("methodCode", method);
        if (bytes != null) {
            b.file(new MockMultipartFile("slip", "slip.jpg", "image/jpeg", bytes));
        }
        return api.perform(b, null, null);
    }

    private long findPending(String staffToken, String orderNo) {
        for (JsonNode p : api.get("/api/v1/admin/payments?status=PENDING&size=100", staffToken).data().get("items")) {
            if (orderNo.equals(p.get("orderNo").asString())) {
                return p.get("payment").get("id").asLong();
            }
        }
        throw new IllegalStateException("pending payment not found for " + orderNo);
    }

    private boolean boardContains(String staffToken, long orderId) {
        for (JsonNode o : api.get("/api/v1/admin/orders/board", staffToken).data()) {
            if (o.get("id").asLong() == orderId) {
                return true;
            }
        }
        return false;
    }
}
