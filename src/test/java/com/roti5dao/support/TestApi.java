package com.roti5dao.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** helper เรียก API ผ่าน MockMvc สำหรับ integration test */
@Component
public class TestApi {

    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(10_000_000);
    public static final String ADMIN_PHONE = "0800000001";
    public static final String ADMIN_INITIAL_PASSWORD = "Admin1234";
    public static final String ADMIN_PASSWORD = "Admin5678";

    private final MockMvc mvc;
    private final JsonMapper json;

    public TestApi(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public record Res(int status, JsonNode body, MvcResult raw) {
        public JsonNode data() {
            return body.get("data");
        }

        public String errorCode() {
            JsonNode err = body.get("error");
            return err == null || err.isNull() ? null : err.get("code").asString();
        }

        public String cookie(String name) {
            Cookie c = raw.getResponse().getCookie(name);
            return c == null ? null : c.getValue();
        }
    }

    public record Session(String accessToken, String refreshToken, long userId) {
    }

    public static String uniquePhone() {
        return "09" + PHONE_SEQ.incrementAndGet();
    }

    // ------------------------------------------------------------------ raw calls

    public Res call(HttpMethod method, String url, String token, Object body) {
        var b = request(method, url);
        return perform(b, token, body);
    }

    public Res get(String url, String token) {
        return perform(request(HttpMethod.GET, url), token, null);
    }

    public Res post(String url, String token, Object body) {
        return call(HttpMethod.POST, url, token, body);
    }

    public Res put(String url, String token, Object body) {
        return call(HttpMethod.PUT, url, token, body);
    }

    public Res patch(String url, String token, Object body) {
        return call(HttpMethod.PATCH, url, token, body);
    }

    public Res perform(AbstractMockHttpServletRequestBuilder<?> b, String token, Object body) {
        try {
            if (token != null) {
                b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            if (body != null) {
                b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
            }
            MvcResult r = mvc.perform(b).andReturn();
            String content = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            JsonNode node = content.isBlank() || !content.startsWith("{") ? json.createObjectNode() : json.readTree(content);
            return new Res(r.getResponse().getStatus(), node, r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ auth helpers

    public Session register(String phone, String password) {
        Res r = post("/api/v1/auth/register", null, Map.of("phone", phone, "password", password, "nickname", "user" + phone));
        if (r.status() != 201) {
            throw new IllegalStateException("register failed: " + r.body());
        }
        return session(r);
    }

    public Session newCustomer() {
        return register(uniquePhone(), "Passw0rd!");
    }

    public Res login(String username, String password) {
        return post("/api/v1/auth/login", null, Map.of("username", username, "password", password));
    }

    public Session loginOk(String username, String password) {
        Res r = login(username, password);
        if (r.status() != 200) {
            throw new IllegalStateException("login failed: " + r.body());
        }
        return session(r);
    }

    /** ADMIN จาก AdminBootstrapRunner — เปลี่ยนรหัสครั้งแรกให้อัตโนมัติ */
    public synchronized String adminToken() {
        // ไม่ cache: test เลื่อนนาฬิกาได้ token เก่าอาจหมดอายุ/มี iat ในอนาคต
        Res r = login(ADMIN_PHONE, ADMIN_PASSWORD);
        if (r.status() != 200) {
            Session first = loginOk(ADMIN_PHONE, ADMIN_INITIAL_PASSWORD);
            Res changed = put("/api/v1/me/password", first.accessToken(),
                    Map.of("currentPassword", ADMIN_INITIAL_PASSWORD, "newPassword", ADMIN_PASSWORD));
            if (changed.status() != 200) {
                throw new IllegalStateException("admin password change failed: " + changed.body());
            }
            return changed.data().get("accessToken").asString();
        } else {
            return r.data().get("accessToken").asString();
        }
    }

    /** สร้างพนักงาน (หรือ admin) แล้ว login + เปลี่ยนรหัสครั้งแรก */
    public Session newStaff(String role) {
        String phone = uniquePhone();
        Res created = post("/api/v1/admin/staff", adminToken(), Map.of("phone", phone, "password", "Staff1234",
                "nickname", role.toLowerCase() + phone, "role", role));
        if (created.status() != 201) {
            throw new IllegalStateException("create staff failed: " + created.body());
        }
        Session first = loginOk(phone, "Staff1234");
        Res changed = put("/api/v1/me/password", first.accessToken(),
                Map.of("currentPassword", "Staff1234", "newPassword", "Staff5678"));
        return new Session(changed.data().get("accessToken").asString(), changed.cookie("r5d_rt"),
                changed.data().get("user").get("id").asLong());
    }

    public Res refresh(String refreshToken) {
        return perform(request(HttpMethod.POST, "/api/v1/auth/refresh").cookie(new Cookie("r5d_rt", refreshToken)), null, null);
    }

    private static Session session(Res r) {
        return new Session(r.data().get("accessToken").asString(), r.cookie("r5d_rt"), r.data().get("user").get("id").asLong());
    }

    // ------------------------------------------------------------------ catalog helpers

    /** id ของสินค้าจาก seed ตาม code */
    public long productId(String code) {
        Res r = get("/api/v1/public/products?keyword=", null);
        for (JsonNode p : r.data()) {
            if (code.equals(p.get("code").asString())) {
                return p.get("id").asLong();
            }
        }
        throw new IllegalStateException("product not found: " + code);
    }

    public JsonMapper json() {
        return json;
    }
}
