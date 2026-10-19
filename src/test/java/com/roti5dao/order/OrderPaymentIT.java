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
import tools.jackson.databind.JsonNode;

class OrderPaymentIT extends IntegrationTest {


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
    void walkInPayLaterGoesToKitchenAndMustPayBeforeComplete() {
        String staff = api.newStaff("STAFF").accessToken();
        long classic = api.productId("โรตีนมข้นน้ำตาล"); // 30 บาท

        Res created = walkIn(staff, Map.of("payLater", true), item(classic, 1));
        JsonNode o = created.data();
        assertThat(o.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(o.get("payLater").asBoolean()).isTrue();
        assertThat(o.get("remainingAmount").asDouble()).isEqualTo(30.0);
        long id = o.get("id").asLong();

        // ขึ้นคิวครัว พร้อมป้ายค้างชำระ
        JsonNode onBoard = null;
        for (JsonNode b : api.get("/api/v1/admin/orders/board", staff).data()) {
            if (b.get("id").asLong() == id) {
                onBoard = b;
            }
        }
        assertThat(onBoard).isNotNull();
        assertThat(onBoard.get("payLater").asBoolean()).isTrue();

        api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "PREPARING"));
        api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "READY"));
        // ยังไม่จ่าย → ปิดออเดอร์ไม่ได้
        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "COMPLETED")).errorCode())
                .isEqualTo("ORDER_INVALID_STATUS");

        // รับเงินระหว่างอยู่ในคิว → ป้ายหาย สถานะไม่ถอยกลับ
        Res paid = api.post("/api/v1/admin/orders/" + id + "/payments", staff, Map.of("methodCode", "CASH", "cashReceived", 50));
        assertThat(paid.data().get("payLater").asBoolean()).isFalse();
        assertThat(paid.data().get("status").asString()).isEqualTo("READY");
        assertThat(paid.data().get("payments").get(0).get("changeAmount").asDouble()).isEqualTo(20.0);
        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "COMPLETED")).status())
                .isEqualTo(200);
    }

    @Test
    void todayQueuePayLaterAndOneShotComplete() {
        String staff = api.newStaff("STAFF").accessToken();
        long classic = api.productId("โรตีนมข้นน้ำตาล"); // 30

        long pending = walkIn(staff, Map.of(), item(classic, 2)).data().get("id").asLong();   // 60 รอชำระ
        long paidOne = walkIn(staff, Map.of(), item(classic, 1)).data().get("id").asLong();   // 30 จ่ายครบ
        api.post("/api/v1/admin/orders/" + paidOne + "/payments", staff, Map.of("methodCode", "CASH"));

        // เปลี่ยนออเดอร์รอชำระ → ยังไม่ชำระ (เข้าคิวครัว)
        Res pl = api.post("/api/v1/admin/orders/" + pending + "/pay-later", staff, null);
        assertThat(pl.data().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(pl.data().get("payLater").asBoolean()).isTrue();
        assertThat(api.post("/api/v1/admin/orders/" + paidOne + "/pay-later", staff, null).errorCode())
                .isEqualTo("ORDER_INVALID_STATUS");

        // รับเงินบางส่วน → remainingAmount ในรายการคิวถูกต้อง
        api.post("/api/v1/admin/orders/" + pending + "/payments", staff, Map.of("methodCode", "CASH", "amount", 20));
        JsonNode queue = api.get("/api/v1/admin/orders?queue=true&date=2026-09-23&size=100", staff).data().get("items");
        JsonNode card = null;
        int lastQueueNo = -1;
        for (JsonNode o : queue) {
            assertThat(o.get("queueNo").asInt()).isGreaterThan(lastQueueNo); // เรียงตามเลขคิว
            lastQueueNo = o.get("queueNo").asInt();
            if (o.get("id").asLong() == pending) {
                card = o;
            }
        }
        assertThat(card).isNotNull();
        assertThat(card.get("remainingAmount").asDouble()).isEqualTo(40.0);

        // ยังค้างเงิน → ปิดไม่ได้ · จ่ายครบแล้วปิดได้ในครั้งเดียวจาก CONFIRMED
        assertThat(api.post("/api/v1/admin/orders/" + pending + "/complete", staff, null).errorCode())
                .isEqualTo("ORDER_INVALID_STATUS");
        api.post("/api/v1/admin/orders/" + pending + "/payments", staff, Map.of("methodCode", "CASH"));
        Res done = api.post("/api/v1/admin/orders/" + pending + "/complete", staff, null);
        assertThat(done.data().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.data().get("completedAt").isNull()).isFalse();
        assertThat(api.post("/api/v1/admin/orders/" + paidOne + "/complete", staff, null).data().get("status").asString())
                .isEqualTo("COMPLETED");
    }

    @Test
    void publicPaymentMethodsCarryShopAccounts() {
        api.put("/api/v1/admin/settings", api.adminToken(),
                Map.of("values", Map.of("payment.promptpay_id", "0812345678", "payment.bank_account", "กสิกร 123-4-56789-0")));
        Map<String, JsonNode> byCode = new HashMap<>();
        for (JsonNode m : api.get("/api/v1/public/payment-methods", null).data()) {
            byCode.put(m.get("code").asString(), m);
        }
        assertThat(byCode).containsKeys("CASH", "TRANSFER", "PROMPTPAY");
        assertThat(byCode.get("PROMPTPAY").get("promptPayId").asString()).isEqualTo("0812345678");
        assertThat(byCode.get("TRANSFER").get("bankAccount").asString()).isEqualTo("กสิกร 123-4-56789-0");
        assertThat(byCode.get("CASH").get("promptPayId").isNull()).isTrue();
    }

    @Test
    void cookFirstPayAtPickupFlow() {
        String staff = api.newStaff("STAFF").accessToken();
        long classic = api.productId("โรตีนมข้นน้ำตาล"); // 30

        // ออนไลน์ guest สั่ง → ขึ้นคิวครัวทันทีทั้งที่ยังไม่จ่าย
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(classic, 2)), "guestName", "บาว"));
        long id = created.data().get("id").asLong();
        String token = created.data().get("trackingToken").asString();
        boolean onBoard = false;
        for (JsonNode b : api.get("/api/v1/admin/orders/board", staff).data()) {
            onBoard |= b.get("id").asLong() == id;
        }
        assertThat(onBoard).isTrue();

        // เริ่มทำ (ยังไม่จ่าย) → สลับคิว กลับไปรอ → เริ่มทำใหม่ → พร้อมรับ
        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "PREPARING"))
                .data().get("status").asString()).isEqualTo("PREPARING");
        Res back = api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "CONFIRMED"));
        assertThat(back.data().get("status").asString()).isEqualTo("PENDING_PAYMENT"); // ยังไม่จ่าย → ระบบเลือกให้
        assertThat(back.data().get("preparingAt").isNull()).isTrue();
        api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "PREPARING"));
        api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "READY"));

        // ยังไม่จ่าย → ปิดไม่ได้ทั้ง PATCH และ complete ที่ไม่แนบการชำระ
        assertThat(api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "COMPLETED")).errorCode())
                .isEqualTo("ORDER_INVALID_STATUS");
        assertThat(api.post("/api/v1/admin/orders/" + id + "/complete", staff, null).errorCode())
                .isEqualTo("ORDER_INVALID_STATUS");

        // ลูกค้าแจ้งว่าจะจ่ายพร้อมเพย์ → กด "เสร็จสิ้น" โดยไม่แนบ body = ยืนยันช่องทางที่ลูกค้าเลือกไว้
        api.post("/api/v1/public/orders/track/" + token + "/payments", null, Map.of("methodCode", "PROMPTPAY"));
        Res done = api.post("/api/v1/admin/orders/" + id + "/complete", staff, null);
        assertThat(done.data().get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.data().get("remainingAmount").asDouble()).isEqualTo(0.0);
        JsonNode pays = done.data().get("payments");
        assertThat(pays.size()).isEqualTo(1);
        assertThat(pays.get(0).get("methodCode").asString()).isEqualTo("PROMPTPAY");
        assertThat(pays.get(0).get("status").asString()).isEqualTo("PAID");

        // track: timeline ครบ
        JsonNode t = api.get("/api/v1/public/orders/track/" + token, null).data();
        assertThat(t.get("preparingAt").isNull()).isFalse();
        assertThat(t.get("readyAt").isNull()).isFalse();
        assertThat(t.get("completedAt").isNull()).isFalse();
        boolean hasPaidAt = false;
        for (JsonNode p : t.get("payments")) {
            hasPaidAt |= "PAID".equals(p.get("status").asString()) && !p.get("paidAt").isNull();
        }
        assertThat(hasPaidAt).isTrue();

        // แนบช่องทางเอง (ต่างจากที่ลูกค้าแจ้ง) → ใช้ของพนักงาน รายการที่ลูกค้าแจ้งถูกปิด
        Res o2 = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(classic, 1)), "guestName", "บี"));
        api.post("/api/v1/public/orders/track/" + o2.data().get("trackingToken").asString() + "/payments", null,
                Map.of("methodCode", "TRANSFER"));
        Res done2 = api.post("/api/v1/admin/orders/" + o2.data().get("id").asLong() + "/complete", staff,
                Map.of("methodCode", "CASH", "cashReceived", 50));
        int pendingLeft = 0;
        boolean cashPaid = false;
        for (JsonNode p : done2.data().get("payments")) {
            pendingLeft += "PENDING".equals(p.get("status").asString()) ? 1 : 0;
            cashPaid |= "CASH".equals(p.get("methodCode").asString()) && "PAID".equals(p.get("status").asString())
                    && p.get("changeAmount").asDouble() == 20.0;
        }
        assertThat(pendingLeft).isZero();
        assertThat(cashPaid).isTrue();

        // ยกเลิกระหว่างทำได้
        long other = walkIn(staff, Map.of(), item(classic, 1)).data().get("id").asLong();
        api.patch("/api/v1/admin/orders/" + other + "/status", staff, Map.of("status", "PREPARING"));
        assertThat(api.post("/api/v1/admin/orders/" + other + "/cancel", staff, Map.of("reason", "ลูกค้าเปลี่ยนใจ"))
                .data().get("status").asString()).isEqualTo("CANCELLED");

        // quote บอกอัตราแลกแต้ม
        assertThat(api.post("/api/v1/public/orders/quote", null, Map.of("items", List.of(item(classic, 1))))
                .data().get("redeemPointsPerBaht").asInt()).isPositive();
    }

    @Test
    void staffAddsUpdatesAndRemovesOrderItems() {
        String staff = api.newStaff("STAFF").accessToken();
        long classic = api.productId("โรตีนมข้นน้ำตาล"); // 30
        long banana = api.productId("โรตีกล้วยหอม");     // 45
        long egg = eggOptionId(classic);                   // +10

        long id = walkIn(staff, Map.of(), item(classic, 1)).data().get("id").asLong();

        // เพิ่ม
        Res added = api.post("/api/v1/admin/orders/" + id + "/items", staff, item(banana, 2));
        assertThat(added.status()).isEqualTo(201);
        assertThat(added.data().get("totalAmount").asDouble()).isEqualTo(120.0);
        assertThat(added.data().get("items").size()).isEqualTo(2);
        long classicItem = added.data().get("items").get(0).get("id").asLong();
        long bananaItem = added.data().get("items").get(1).get("id").asLong();

        // แก้: classic เป็น 2 ชิ้น + ไข่ → (30+10)*2 = 80, items id ใหม่หลังคำนวณ
        Res updated = api.put("/api/v1/admin/orders/" + id + "/items/" + classicItem, staff, item(classic, 2, egg));
        assertThat(updated.data().get("totalAmount").asDouble()).isEqualTo(170.0);

        // ลบกล้วย (หา id ใหม่จาก response)
        long newBanana = -1;
        for (JsonNode i : updated.data().get("items")) {
            if (i.get("productId").asLong() == banana) {
                newBanana = i.get("id").asLong();
            }
        }
        Res removed = api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/orders/" + id + "/items/" + newBanana, staff, null);
        assertThat(removed.data().get("totalAmount").asDouble()).isEqualTo(80.0);
        assertThat(removed.data().get("orderNo").asString()).isEqualTo(updated.data().get("orderNo").asString());

        // ลบรายการสุดท้ายไม่ได้ / item ที่ไม่มีอยู่ = 404
        long last = removed.data().get("items").get(0).get("id").asLong();
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/orders/" + id + "/items/" + last, staff, null)
                .errorCode()).isEqualTo("INVALID_OPERATION");
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/orders/" + id + "/items/" + bananaItem, staff, null)
                .status()).isEqualTo(404);

        // จ่ายครบ 80 → CONFIRMED แล้วเพิ่มของ → ยอดเพิ่ม กลายเป็นค้างชำระ
        api.post("/api/v1/admin/orders/" + id + "/payments", staff, Map.of("methodCode", "CASH", "cashReceived", 80));
        Res more = api.post("/api/v1/admin/orders/" + id + "/items", staff, item(classic, 1));
        assertThat(more.data().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(more.data().get("payLater").asBoolean()).isTrue();
        assertThat(more.data().get("remainingAmount").asDouble()).isEqualTo(30.0);

        // ลดยอดต่ำกว่าที่จ่ายแล้วไม่ได้
        long extra = more.data().get("items").get(1).get("id").asLong();
        long first = more.data().get("items").get(0).get("id").asLong();
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/orders/" + id + "/items/" + first, staff, null)
                .errorCode()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        // เอาส่วนที่เพิ่มออก → กลับมาจ่ายครบ ป้ายค้างชำระหาย
        Res back = api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/orders/" + id + "/items/" + extra, staff, null);
        assertThat(back.data().get("payLater").asBoolean()).isFalse();

        // ครัวเริ่มทำแล้ว → แก้ไม่ได้
        api.patch("/api/v1/admin/orders/" + id + "/status", staff, Map.of("status", "PREPARING"));
        assertThat(api.post("/api/v1/admin/orders/" + id + "/items", staff, item(classic, 1)).errorCode())
                .isEqualTo("ORDER_INVALID_STATUS");
    }

    @Test
    void walkInGuestPaysCashAndGetsChange() {
        var staff = api.newStaff("STAFF");
        long classic = api.productId("โรตีนมข้นน้ำตาล"); // 30 บาท
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
        long id = walkIn(staff.accessToken(), Map.of(), item(api.productId("ไมโลเย็น"), 1)).data().get("id").asLong();

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
    void onlineGuestPhoneIsOptional() {
        long banana = api.productId("โรตีกล้วยหอม");
        // ไม่กรอกเบอร์ / ส่งค่าว่าง → สั่งได้
        Res noPhone = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(banana, 1)), "guestName", "โต๊ะ 3"));
        assertThat(noPhone.status()).isEqualTo(201);
        assertThat(noPhone.data().get("guestPhone").isNull()).isTrue();
        Res blank = api.post("/api/v1/public/orders", null,
                Map.of("items", List.of(item(banana, 1)), "guestName", "โต๊ะ 4", "guestPhone", ""));
        assertThat(blank.status()).isEqualTo(201);
        assertThat(api.get("/api/v1/public/orders/track/" + blank.data().get("trackingToken").asString(), null)
                .data().get("maskedPhone").isNull()).isTrue();
        // กรอกมาแต่ผิดรูปแบบ → ไม่ผ่าน · ไม่มีชื่อ → ไม่ผ่าน
        assertThat(api.post("/api/v1/public/orders", null,
                Map.of("items", List.of(item(banana, 1)), "guestName", "x", "guestPhone", "123")).errorCode())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(banana, 1)))).errorCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void onlineGuestSelectsMethodAndStaffApproves() {
        long banana = api.productId("โรตีกล้วยหอม"); // 45
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(banana, 2)),
                "guestName", "คุณเอ", "guestPhone", "081-234-5678"));
        assertThat(created.status()).isEqualTo(201);
        String token = created.data().get("trackingToken").asString();
        assertThat(created.data().get("totalAmount").asDouble()).isEqualTo(90.0);

        Res track = api.get("/api/v1/public/orders/track/" + token, null);
        assertThat(track.data().get("maskedPhone").asString()).isEqualTo("081-xxx-5678");
        assertThat(track.data().has("trackingToken")).isFalse();

        // ออนไลน์เลือกได้ 3 ช่องทาง (เงินสด/โอน/พร้อมเพย์) — บัตรใช้ได้เฉพาะหน้าร้าน
        List<String> online = new java.util.ArrayList<>();
        for (JsonNode m : api.get("/api/v1/public/payment-methods", null).data()) {
            online.add(m.get("code").asString());
        }
        assertThat(online).containsExactly("CASH", "TRANSFER", "PROMPTPAY");
        assertThat(pay(token, "CARD").errorCode()).isEqualTo("PAYMENT_METHOD_UNAVAILABLE");

        Res submitted = pay(token, "PROMPTPAY");
        assertThat(submitted.status()).isEqualTo(201);
        assertThat(submitted.data().get("payments").get(0).get("status").asString()).isEqualTo("PENDING");

        var staff = api.newStaff("STAFF");
        long paymentId = findPending(staff.accessToken(), created.data().get("orderNo").asString());


        Res approved = api.patch("/api/v1/admin/payments/" + paymentId + "/verify", staff.accessToken(), Map.of("approve", true));
        assertThat(approved.data().get("status").asString()).isEqualTo("PAID");
        assertThat(api.get("/api/v1/public/orders/track/" + token, null).data().get("status").asString()).isEqualTo("CONFIRMED");

        // ตรวจซ้ำไม่ได้
        assertThat(api.patch("/api/v1/admin/payments/" + paymentId + "/verify", staff.accessToken(), Map.of("approve", true))
                .errorCode()).isEqualTo("INVALID_OPERATION");
    }

    @Test
    void rejectedPaymentKeepsOrderPending() {
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(api.productId("ชาชัก"), 1)),
                "guestName", "คุณบี", "guestPhone", "0891112222"));
        String token = created.data().get("trackingToken").asString();
        pay(token, "TRANSFER");
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
    void pendingPaymentSpamLimited() {
        Res created = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(api.productId("ชาชัก"), 1)),
                "guestName", "spam", "guestPhone", "0891113333"));
        String token = created.data().get("trackingToken").asString();
        for (int i = 0; i < 3; i++) {
            assertThat(pay(token, "TRANSFER").status()).isEqualTo(201);
        }
        assertThat(pay(token, "TRANSFER").errorCode()).isEqualTo("TOO_MANY_PENDING_PAYMENTS");
    }

    @Test
    void mixedPaymentCashPlusCard() {
        var staff = api.newStaff("STAFF");
        long id = walkIn(staff.accessToken(), Map.of(), item(api.productId("มะตะบะเนื้อ"), 1)).data().get("id").asLong(); // 80

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
        long classic = api.productId("โรตีนมข้นน้ำตาล");
        long curry = api.productId("โรตีแกงกะหรี่ไก่");
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

        Res order = walkIn(staff.accessToken(), Map.of("customerPhone", phone), item(api.productId("ไมโลเย็น"), 1));
        assertThat(order.data().get("customer").get("id").asLong()).isEqualTo(customer.userId());
        assertThat(walkIn(staff.accessToken(), Map.of("customerPhone", "0870000000"), item(api.productId("ไมโลเย็น"), 1))
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
        long id = walkIn(staff.accessToken(), Map.of(), item(api.productId("ไมโลเย็น"), 1)).data().get("id").asLong();
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
            Res r = api.post("/api/v1/public/orders", null, Map.of("items", List.of(item(api.productId("ไมโลเย็น"), 1)),
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
        long milo = api.productId("ไมโลเย็น");
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

    private Res pay(String token, String method) {
        return api.post("/api/v1/public/orders/track/" + token + "/payments", null, Map.of("methodCode", method));
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
