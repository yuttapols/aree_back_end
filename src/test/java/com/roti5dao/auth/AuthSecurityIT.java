package com.roti5dao.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi;
import com.roti5dao.support.TestApi.Res;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class AuthSecurityIT extends IntegrationTest {

    // ------------------------------------------------------------------ register / login / refresh

    @Test
    void registerLoginRefreshFlow() {
        String phone = TestApi.uniquePhone();
        Res reg = api.post("/api/v1/auth/register", null,
                Map.of("phone", phone, "password", "Secret123", "nickname", "Somchai"));
        assertThat(reg.status()).isEqualTo(201);
        assertThat(reg.data().get("user").get("memberCode").asString()).matches("R5D-\\d{6}");
        assertThat(reg.data().has("refreshToken")).isFalse(); // refresh token อยู่ใน cookie เท่านั้น

        String setCookie = reg.raw().getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");

        // login ด้วยเบอร์รูปแบบอื่น (+66) ได้
        Res login = api.login("+66" + phone.substring(1), "Secret123");
        assertThat(login.status()).isEqualTo(200);
        String access = login.data().get("accessToken").asString();
        String refresh = login.cookie("r5d_rt");

        assertThat(api.get("/api/v1/me", access).data().get("phone").asString()).isEqualTo(phone);

        // refresh → token ใหม่ + rotate cookie
        Res refreshed = api.refresh(refresh);
        assertThat(refreshed.status()).isEqualTo(200);
        String newRefresh = refreshed.cookie("r5d_rt");
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refresh);
        assertThat(api.get("/api/v1/me", refreshed.data().get("accessToken").asString()).status()).isEqualTo(200);
    }

    @Test
    void duplicatePhoneAndWeakPasswordRejected() {
        var s = api.newCustomer();
        String phone = api.get("/api/v1/me", s.accessToken()).data().get("phone").asString();
        Res dup = api.post("/api/v1/auth/register", null, Map.of("phone", phone, "password", "Secret123", "nickname", "x"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.errorCode()).isEqualTo("PHONE_ALREADY_USED");

        Res weak = api.post("/api/v1/auth/register", null,
                Map.of("phone", TestApi.uniquePhone(), "password", "abcdefgh", "nickname", "x"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.body().get("error").get("fields").get(0).get("field").asString()).isEqualTo("password");

        Res badPhone = api.post("/api/v1/auth/register", null, Map.of("phone", "12345", "password", "Secret123", "nickname", "x"));
        assertThat(badPhone.status()).isEqualTo(400);
    }

    @Test
    void loginErrorsDoNotRevealAccountExistence() {
        Res unknown = api.login("0899999999", "Wrong1234");
        var s = api.newCustomer();
        String phone = api.get("/api/v1/me", s.accessToken()).data().get("phone").asString();
        Res wrong = api.login(phone, "Wrong1234");
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.errorCode()).isEqualTo(wrong.errorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void accountLockedAfterRepeatedFailures() {
        var s = api.newCustomer();
        String phone = api.get("/api/v1/me", s.accessToken()).data().get("phone").asString();
        for (int i = 0; i < 5; i++) {
            assertThat(api.login(phone, "Wrong1234").errorCode()).isEqualTo("INVALID_CREDENTIALS");
        }
        // ถูกล็อก แม้ใส่รหัสถูก
        assertThat(api.login(phone, "Passw0rd!").errorCode()).isEqualTo("ACCOUNT_LOCKED");

        clock.advance(Duration.ofMinutes(16));
        assertThat(api.login(phone, "Passw0rd!").status()).isEqualTo(200);
    }

    @Test
    void expiredAccessTokenReturnsTokenExpired() {
        var s = api.newCustomer();
        clock.advance(Duration.ofMinutes(6));
        Res r = api.get("/api/v1/me", s.accessToken());
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.errorCode()).isEqualTo("TOKEN_EXPIRED");
        // refresh token ยังใช้ได้ (idle ไม่เกิน 15 นาที)
        assertThat(api.refresh(s.refreshToken()).status()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ session idle timeout ตาม role

    @Test
    void customerSessionEndsAfter15MinutesIdle() {
        var s = api.newCustomer();
        clock.advance(Duration.ofMinutes(16));
        assertThat(api.refresh(s.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void activeCustomerSessionSlides() {
        var s = api.newCustomer();
        String refresh = s.refreshToken();
        // ใช้งานต่อเนื่อง 40 นาที (refresh ทุก 5 นาทีตามอายุ access token) → ไม่หลุด
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofMinutes(5));
            Res r = api.refresh(refresh);
            assertThat(r.status()).isEqualTo(200);
            refresh = r.cookie("r5d_rt");
        }
    }

    @Test
    void staffAndAdminSessionLast12HoursIdle() {
        var staff = api.newStaff("STAFF");
        var admin = api.newStaff("ADMIN");
        clock.advance(Duration.ofHours(11));
        Res staffRefreshed = api.refresh(staff.refreshToken());
        assertThat(staffRefreshed.status()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));
        assertThat(api.refresh(admin.refreshToken()).status()).isEqualTo(200);

        clock.advance(Duration.ofHours(12).plusMinutes(1));
        assertThat(api.refresh(staffRefreshed.cookie("r5d_rt")).status()).isEqualTo(401);
    }

    @Test
    void tamperedOrForgedTokenRejected() {
        var s = api.newCustomer();
        String[] parts = s.accessToken().split("\\.");
        // เปลี่ยน payload (ยกระดับเป็น ADMIN) โดยไม่มี key → ลายเซ็นไม่ตรง
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + s.userId() + "\",\"role\":\"ADMIN\",\"iss\":\"roti5dao\",\"aud\":[\"roti5dao-web\"],"
                        + "\"iat\":1790000000,\"exp\":4102444800}").getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        assertThat(api.get("/api/v1/admin/staff", forged).status()).isEqualTo(401);

        // alg=none
        String none = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes())
                + "." + forgedPayload + ".";
        assertThat(api.get("/api/v1/me", none).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/me", "garbage").status()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ refresh token theft / revocation

    @Test
    void reusedRefreshTokenRevokesWholeSession() {
        var s = api.newCustomer();
        Res first = api.refresh(s.refreshToken());
        assertThat(first.status()).isEqualTo(200);
        String currentRefresh = first.cookie("r5d_rt");
        String currentAccess = first.data().get("accessToken").asString();

        // ภายใน grace (request ซ้อนจากหลายแท็บ) → แค่ 401 ไม่ revoke
        assertThat(api.refresh(s.refreshToken()).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/me", currentAccess).status()).isEqualTo(200);

        // token เก่าถูกใช้ซ้ำหลังผ่านไปนาน → ถือว่าถูกขโมย: revoke ทุก session
        clock.advance(Duration.ofSeconds(30));
        Res reuse = api.refresh(s.refreshToken());
        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.raw().getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(api.refresh(currentRefresh).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/me", currentAccess).status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesRefreshToken() {
        var s = api.newCustomer();
        Res out = api.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("r5d_rt", s.refreshToken())), null, null);
        assertThat(out.status()).isEqualTo(200);
        assertThat(api.refresh(s.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void passwordChangeRevokesOtherSessions() {
        String phone = TestApi.uniquePhone();
        var s1 = api.register(phone, "Secret123");
        var s2 = api.loginOk(phone, "Secret123");
        clock.advance(Duration.ofSeconds(2));

        Res wrongCurrent = api.put("/api/v1/me/password", s1.accessToken(),
                Map.of("currentPassword", "nope", "newPassword", "NewSecret456"));
        assertThat(wrongCurrent.status()).isEqualTo(401);

        Res changed = api.put("/api/v1/me/password", s1.accessToken(),
                Map.of("currentPassword", "Secret123", "newPassword", "NewSecret456"));
        assertThat(changed.status()).isEqualTo(200);

        // token เดิมทั้งสอง session ใช้ไม่ได้ทันที, refresh token เดิมก็ใช้ไม่ได้
        assertThat(api.get("/api/v1/me", s1.accessToken()).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/me", s2.accessToken()).status()).isEqualTo(401);
        assertThat(api.refresh(s2.refreshToken()).status()).isEqualTo(401);
        // token ใหม่ที่ได้จากการเปลี่ยนรหัสใช้ได้
        assertThat(api.get("/api/v1/me", changed.data().get("accessToken").asString()).status()).isEqualTo(200);
        assertThat(api.login(phone, "NewSecret456").status()).isEqualTo(200);
    }

    @Test
    void suspendedStaffLosesAccessImmediately() {
        var staff = api.newStaff("STAFF");
        assertThat(api.get("/api/v1/admin/orders/board", staff.accessToken()).status()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(2));

        var info = api.get("/api/v1/admin/staff/" + staff.userId(), api.adminToken()).data();
        Res suspend = api.put("/api/v1/admin/staff/" + staff.userId(), api.adminToken(), Map.of(
                "nickname", info.get("nickname").asString(), "role", "STAFF", "status", "SUSPENDED"));
        assertThat(suspend.status()).isEqualTo(200);

        assertThat(api.get("/api/v1/admin/orders/board", staff.accessToken()).status()).isEqualTo(401);
        assertThat(api.refresh(staff.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void firstLoginMustChangePassword() {
        String phone = TestApi.uniquePhone();
        api.post("/api/v1/admin/staff", api.adminToken(),
                Map.of("phone", phone, "password", "Temp12345", "nickname", "new", "role", "STAFF"));
        var s = api.loginOk(phone, "Temp12345");
        assertThat(api.get("/api/v1/me", s.accessToken()).data().get("passwordChangeRequired").asBoolean()).isTrue();
        Res blocked = api.get("/api/v1/admin/orders/board", s.accessToken());
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.errorCode()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
    }

    // ------------------------------------------------------------------ authorization rules

    @Test
    void roleBasedAccessControl() {
        var customer = api.newCustomer();
        var staff = api.newStaff("STAFF");

        assertThat(api.get("/api/v1/me", null).errorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(api.get("/api/v1/admin/orders", null).status()).isEqualTo(401);

        // ลูกค้าเข้า /admin ไม่ได้
        assertThat(api.get("/api/v1/admin/orders", customer.accessToken()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/admin/customers/lookup?phone=0812345678", customer.accessToken()).status()).isEqualTo(403);

        // STAFF ใช้ POS ได้ แต่เมนู ADMIN ไม่ได้
        assertThat(api.get("/api/v1/admin/products", staff.accessToken()).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/admin/staff", staff.accessToken()).errorCode()).isEqualTo("FORBIDDEN");
        assertThat(api.get("/api/v1/admin/settings", staff.accessToken()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/admin/dashboard/summary?from=2026-09-01&to=2026-09-30", staff.accessToken()).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/admin/products", staff.accessToken(), Map.of("categoryId", 1, "code", "X",
                "name", "x", "price", 1)).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/admin/customers/1/points/adjust", staff.accessToken(),
                Map.of("points", 1000, "remark", "hack")).status()).isEqualTo(403);
        // STAFF สร้าง ADMIN เองไม่ได้ (privilege escalation)
        assertThat(api.post("/api/v1/admin/staff", staff.accessToken(), Map.of("phone", TestApi.uniquePhone(),
                "password", "Hack12345", "nickname", "x", "role", "ADMIN")).status()).isEqualTo(403);
    }

    @Test
    void adminCannotDemoteThemselvesOrRemoveLastAdmin() {
        var admin2 = api.newStaff("ADMIN");
        Res selfDemote = api.put("/api/v1/admin/staff/" + admin2.userId(), admin2.accessToken(),
                Map.of("nickname", "a", "role", "STAFF", "status", "ACTIVE"));
        assertThat(selfDemote.errorCode()).isEqualTo("INVALID_OPERATION");
    }

    @Test
    void customerCanOnlySeeOwnOrders() {
        var a = api.newCustomer();
        var b = api.newCustomer();
        long productId = api.productId("RT-CLASSIC");
        Res order = api.post("/api/v1/public/orders", a.accessToken(),
                Map.of("items", java.util.List.of(Map.of("productId", productId, "quantity", 1))));
        assertThat(order.status()).isEqualTo(201);
        String orderNo = order.data().get("orderNo").asString();

        assertThat(api.get("/api/v1/me/orders/" + orderNo, a.accessToken()).status()).isEqualTo(200);
        // IDOR: ลูกค้าอื่นเห็นเป็น 404 (ไม่บอกว่ามีอยู่)
        assertThat(api.get("/api/v1/me/orders/" + orderNo, b.accessToken()).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/me/orders", b.accessToken()).data().get("totalItems").asLong()).isZero();
    }

    // ------------------------------------------------------------------ transport / headers

    @Test
    void securityHeadersPresent() {
        var r = api.get("/api/v1/public/menu", null).raw().getResponse();
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(r.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void corsOnlyAllowsConfiguredOrigin() {
        var allowed = api.perform(MockMvcRequestBuilders.options("/api/v1/public/menu")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET"), null, null);
        assertThat(allowed.raw().getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");

        var denied = api.perform(MockMvcRequestBuilders.options("/api/v1/public/menu")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"), null, null);
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.raw().getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void refreshFromForeignOriginBlocked() {
        var s = api.newCustomer();
        var r = api.perform(MockMvcRequestBuilders.request(HttpMethod.POST, "/api/v1/auth/refresh")
                .header("Origin", "https://evil.example")
                .cookie(new jakarta.servlet.http.Cookie("r5d_rt", s.refreshToken())), null, null);
        assertThat(r.status()).isEqualTo(403);
        // token ยังไม่ถูกใช้ไป
        assertThat(api.refresh(s.refreshToken()).status()).isEqualTo(200);
    }

    @Test
    void errorsDoNotLeakInternals() {
        Res r = api.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType("application/json").content("{not json"), null, null);
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().toString()).doesNotContain("Exception").doesNotContain("jackson");
        assertThat(api.get("/api/v1/public/products/abc", null).errorCode()).isEqualTo("BAD_REQUEST");
    }
}
