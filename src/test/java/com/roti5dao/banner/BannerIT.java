package com.roti5dao.banner;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi.Res;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

class BannerIT extends IntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private Res upload(String token, byte[] bytes) {
        return api.perform(MockMvcRequestBuilders.multipart("/api/v1/admin/banners/images")
                .file(new MockMultipartFile("file", "b.png", "image/png", bytes)), token, null);
    }

    private String uploadedUrl() {
        Res r = upload(api.adminToken(), PNG);
        assertThat(r.status()).isEqualTo(201);
        return r.data().get("url").asString();
    }

    private Map<String, Object> banner(String placement, String title) {
        var m = new HashMap<String, Object>();
        m.put("placement", placement);
        m.put("imageUrl", uploadedUrl());
        m.put("title", title);
        m.put("ctaLabel", "สั่งเลย");
        m.put("linkUrl", "/menu");
        m.put("active", true);
        return m;
    }

    private List<String> publicTitles(String placement) {
        List<String> titles = new ArrayList<>();
        for (JsonNode b : api.get("/api/v1/public/banners?placement=" + placement, null).data()) {
            titles.add(b.get("title").asString());
        }
        return titles;
    }

    @Test
    void adminManagesHeroSlidesAndPublicSeesOnlyVisibleInOrder() {
        String admin = api.adminToken();
        long a = api.post("/api/v1/admin/banners", admin, banner("HOME_HERO", "A")).data().get("id").asLong();
        long b = api.post("/api/v1/admin/banners", admin, banner("HOME_HERO", "B")).data().get("id").asLong();

        var hidden = banner("HOME_HERO", "ปิดอยู่");
        hidden.put("active", false);
        long c = api.post("/api/v1/admin/banners", admin, hidden).data().get("id").asLong();

        var future = banner("HOME_HERO", "อนาคต");
        future.put("startAt", START.plus(Duration.ofDays(1)).toString());
        long d = api.post("/api/v1/admin/banners", admin, future).data().get("id").asLong();

        assertThat(publicTitles("HOME_HERO")).containsExactly("A", "B");
        assertThat(publicTitles("HOME_OFFER")).doesNotContain("A", "B");

        // จัดลำดับใหม่ — ต้องส่งครบทุกใบ
        assertThat(api.put("/api/v1/admin/banners/order", admin, Map.of("placement", "HOME_HERO", "ids", List.of(b, a)))
                .errorCode()).isEqualTo("VALIDATION_ERROR");
        Res reordered = api.put("/api/v1/admin/banners/order", admin,
                Map.of("placement", "HOME_HERO", "ids", List.of(b, a, c, d)));
        assertThat(reordered.status()).isEqualTo(200);
        assertThat(publicTitles("HOME_HERO")).containsExactly("B", "A");

        // แก้ + ลบ
        var edit = banner("HOME_HERO", "A2");
        edit.put("sortOrder", 0);
        assertThat(api.put("/api/v1/admin/banners/" + a, admin, edit).data().get("title").asString()).isEqualTo("A2");
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/banners/" + b, admin, null).status())
                .isEqualTo(200);
        assertThat(api.get("/api/v1/admin/banners/" + b, admin).status()).isEqualTo(404);

        // ถึงเวลาเริ่ม → แสดง (เลื่อนเวลาเป็นขั้นสุดท้าย — access token ของ admin จะหมดอายุ)
        clock.advance(Duration.ofDays(2));
        assertThat(publicTitles("HOME_HERO")).containsExactly("A2", "อนาคต");
    }

    @Test
    void validatesInputAndPermissions() {
        String admin = api.adminToken();
        var external = banner("HOME_OFFER", "x");
        external.put("imageUrl", "https://evil.example.com/a.png");
        assertThat(api.post("/api/v1/admin/banners", admin, external).errorCode()).isEqualTo("VALIDATION_ERROR");

        var js = banner("HOME_OFFER", "x");
        js.put("linkUrl", "javascript:alert(1)");
        assertThat(api.post("/api/v1/admin/banners", admin, js).errorCode()).isEqualTo("VALIDATION_ERROR");

        for (String openRedirect : new String[] {"//evil.com", "/\\evil.com"}) {
            var link = banner("HOME_OFFER", "x");
            link.put("linkUrl", openRedirect);
            assertThat(api.post("/api/v1/admin/banners", admin, link).errorCode()).isEqualTo("VALIDATION_ERROR");
        }

        var badPromo = banner("HOME_OFFER", "x");
        badPromo.put("promotionId", 999_999);
        assertThat(api.post("/api/v1/admin/banners", admin, badPromo).errorCode()).isEqualTo("VALIDATION_ERROR");

        var badPeriod = banner("HOME_OFFER", "x");
        badPeriod.put("startAt", START.toString());
        badPeriod.put("endAt", START.minusSeconds(60).toString());
        assertThat(api.post("/api/v1/admin/banners", admin, badPeriod).errorCode()).isEqualTo("VALIDATION_ERROR");

        // รูปเกิน 2MB
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThat(upload(admin, big).errorCode()).isEqualTo("PAYLOAD_TOO_LARGE");

        // STAFF / ลูกค้า จัดการไม่ได้
        assertThat(api.get("/api/v1/admin/banners", api.newStaff("STAFF").accessToken()).status()).isEqualTo(403);
        assertThat(upload(api.newCustomer().accessToken(), PNG).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/public/banners?placement=NOPE", null).status()).isEqualTo(400);
    }
}
