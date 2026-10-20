package com.roti5dao.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi.Res;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

class CatalogAdminIT extends IntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private Res upload(String url, String token, String filename, String contentType, byte[] bytes) {
        return api.perform(MockMvcRequestBuilders.multipart(url)
                .file(new MockMultipartFile("file", filename, contentType, bytes)), token, null);
    }

    private long createCategory(String slug) {
        Res r = api.post("/api/v1/admin/categories", api.adminToken(),
                Map.of("name", "หมวด " + slug, "slug", slug, "sortOrder", 9, "active", true));
        assertThat(r.status()).isEqualTo(201);
        return r.data().get("id").asLong();
    }

    private Map<String, Object> product(long categoryId, String code, double price) {
        return new java.util.HashMap<>(Map.of("categoryId", categoryId, "code", code, "name", "สินค้า " + code,
                "price", price, "available", true, "recommended", false, "active", true, "sortOrder", 1));
    }

    @Test
    void adminManagesMenuAndLandingReflectsChanges() {
        long cat = createCategory("test-cat-a");
        Res created = api.post("/api/v1/admin/products", api.adminToken(), product(cat, "TST-A1", 50));
        assertThat(created.status()).isEqualTo(201);
        long id = created.data().get("id").asLong();

        assertThat(publicProduct(id).get("price").asDouble()).isEqualTo(50.0);

        // แก้ราคา → public เห็นทันที
        var update = product(cat, "TST-A1", 55);
        assertThat(api.put("/api/v1/admin/products/" + id, api.adminToken(), update).status()).isEqualTo(200);
        assertThat(publicProduct(id).get("price").asDouble()).isEqualTo(55.0);

        // พนักงานกดของหมดได้
        var staff = api.newStaff("STAFF");
        assertThat(api.patch("/api/v1/admin/products/" + id + "/availability", staff.accessToken(),
                Map.of("available", false)).status()).isEqualTo(200);
        assertThat(publicProduct(id).get("available").asBoolean()).isFalse();

        // ลบ ≠ ปิดใช้งาน → หายทั้ง public และหลังบ้าน และนำรหัสกลับมาใช้ใหม่ได้
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/products/" + id,
                api.adminToken(), null).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/public/products/" + id, null).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/admin/products/" + id, api.adminToken()).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/admin/products", api.adminToken(), product(cat, "TST-A1", 50)).status()).isEqualTo(201);
    }

    @Test
    void productStorefrontFieldsSavedAndReturned() {
        long cat = createCategory("test-cat-i18n");
        var body = product(cat, "X", 15);
        body.put("nameEn", "Plain roti");
        body.put("descriptionEn", "");
        body.put("originalPrice", 20.00);
        body.put("badge", "new");
        Res created = api.post("/api/v1/admin/products", api.adminToken(), body);
        assertThat(created.status()).isEqualTo(201);
        long id = created.data().get("id").asLong();
        assertThat(created.data().get("nameEn").asString()).isEqualTo("Plain roti");
        assertThat(created.data().get("descriptionEn").isNull()).isTrue(); // "" → null
        assertThat(created.data().get("originalPrice").asDouble()).isEqualTo(20.0);

        JsonNode pub = publicProduct(id);
        assertThat(pub.get("badge").asString()).isEqualTo("new");
        assertThat(pub.get("originalPrice").asDouble()).isEqualTo(20.0);
        assertThat(pub.get("nameEn").asString()).isEqualTo("Plain roti");

        // originalPrice ต้อง > price, badge ต้องอยู่ในรายการ
        body.put("originalPrice", 15.00);
        Res badPrice = api.put("/api/v1/admin/products/" + id, api.adminToken(), body);
        assertThat(badPrice.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(badPrice.body().get("error").get("fields").get(0).get("field").asString()).isEqualTo("originalPrice");
        body.put("originalPrice", null);
        body.put("badge", "hot");
        Res badBadge = api.put("/api/v1/admin/products/" + id, api.adminToken(), body);
        assertThat(badBadge.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(badBadge.body().get("error").get("fields").get(0).get("field").asString()).isEqualTo("badge");

        // ลบป้าย/ราคาขีดฆ่าได้ด้วยการส่ง null
        body.put("badge", null);
        Res cleared = api.put("/api/v1/admin/products/" + id, api.adminToken(), body);
        assertThat(cleared.data().get("badge").isNull()).isTrue();
        assertThat(cleared.data().get("originalPrice").isNull()).isTrue();
    }

    @Test
    void missingRequiredNumberReportsFieldName() {
        // ไม่ส่ง sortOrder (int) → 400 พร้อมบอกชื่อฟิลด์
        Res r = api.post("/api/v1/admin/option-groups", api.adminToken(),
                Map.of("name", "x", "minSelect", 0, "maxSelect", 1, "active", true, "items", List.of()));
        assertThat(r.errorCode()).isEqualTo("BAD_REQUEST");
        assertThat(r.body().get("error").get("fields").get(0).get("field").asString()).isEqualTo("sortOrder");
    }

    @Test
    void productCodeGeneratedFromIdsAndImmutable() {
        long cat = createCategory("test-cat-code");
        Res created = api.post("/api/v1/admin/products", api.adminToken(), product(cat, "IGNORED", 25));
        long id = created.data().get("id").asLong();
        String expected = "RT-%03d%04d".formatted(cat, id);
        assertThat(created.data().get("code").asString()).isEqualTo(expected).hasSize(10);

        // ส่ง code มาตอนแก้ก็ไม่เปลี่ยน
        var edit = product(cat, "RT-HACK", 30);
        assertThat(api.put("/api/v1/admin/products/" + id, api.adminToken(), edit).data().get("code").asString())
                .isEqualTo(expected);
    }

    @Test
    void categoryWithoutSortOrderAndWithNameEn() {
        // body เดียวกับที่ FE ส่ง (ไม่มี sortOrder)
        Res created = api.post("/api/v1/admin/categories", api.adminToken(), Map.of("name", "โรตีพิเศษ", "nameEn", "Special roti",
                "slug", "test-no-sort", "description", "", "icon", "pi pi-gift", "active", true));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.data().get("nameEn").asString()).isEqualTo("Special roti");
        int order = created.data().get("sortOrder").asInt();
        assertThat(order).isPositive(); // ต่อท้าย
        long id = created.data().get("id").asLong();

        Res updated = api.put("/api/v1/admin/categories/" + id, api.adminToken(), Map.of("name", "โรตีพิเศษ 2", "nameEn", "",
                "slug", "test-no-sort", "description", "", "icon", "pi pi-gift", "active", true));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.data().get("sortOrder").asInt()).isEqualTo(order); // ไม่ส่ง = คงเดิม
        assertThat(updated.data().get("nameEn").isNull()).isTrue();

        // slug ซ้ำกับหมวดที่มีอยู่ (เช่น "roti" จากเมนูเริ่มต้น) → 409
        assertThat(api.post("/api/v1/admin/categories", api.adminToken(), Map.of("name", "x", "slug", "test-no-sort", "active", true))
                .errorCode()).isEqualTo("DUPLICATE_VALUE");
    }

    @Test
    void categoryDeleteIsSeparateFromDeactivate() {
        long cat = createCategory("test-cat-del");
        long pid = api.post("/api/v1/admin/products", api.adminToken(), product(cat, "TST-DEL1", 30)).data().get("id").asLong();

        // ยังมีสินค้า → ลบไม่ได้
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/categories/" + cat,
                api.adminToken(), null).errorCode()).isEqualTo("INVALID_OPERATION");

        // ปิดใช้งาน → ยังเห็นในหลังบ้าน
        assertThat(api.put("/api/v1/admin/categories/" + cat, api.adminToken(),
                Map.of("name", "หมวด test-cat-del", "slug", "test-cat-del", "sortOrder", 9, "active", false)).status())
                .isEqualTo(200);
        assertThat(categoryIds()).contains(cat);

        // ลบสินค้าก่อน แล้วลบหมวดได้ → หายจากหลังบ้าน, slug ใช้ใหม่ได้
        api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/products/" + pid, api.adminToken(), null);
        assertThat(api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/admin/categories/" + cat,
                api.adminToken(), null).status()).isEqualTo(200);
        assertThat(categoryIds()).doesNotContain(cat);
        createCategory("test-cat-del");
    }

    private List<Long> categoryIds() {
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode c : api.get("/api/v1/admin/categories", api.adminToken()).data()) {
            ids.add(c.get("id").asLong());
        }
        return ids;
    }

    @Test
    void duplicateCodeAndSlugRejected() {
        createCategory("test-dup");
        Res dupSlug = api.post("/api/v1/admin/categories", api.adminToken(),
                Map.of("name", "x", "slug", "test-dup", "sortOrder", 1, "active", true));
        assertThat(dupSlug.errorCode()).isEqualTo("DUPLICATE_VALUE");


        Res badSlug = api.post("/api/v1/admin/categories", api.adminToken(),
                Map.of("name", "x", "slug", "../../etc", "sortOrder", 1, "active", true));
        assertThat(badSlug.status()).isEqualTo(400);
    }

    @Test
    void optionGroupsShownOnProductDetail() {
        long cat = createCategory("test-opt");
        long productId = api.post("/api/v1/admin/products", api.adminToken(), product(cat, "TST-OPT", 40)).data().get("id").asLong();
        Res group = api.post("/api/v1/admin/option-groups", api.adminToken(), Map.of("name", "ความหวาน",
                "minSelect", 1, "maxSelect", 1, "defaultSelected", true, "active", true, "sortOrder", 1,
                "items", List.of(Map.of("name", "หวานน้อย", "extraPrice", 0, "available", true, "sortOrder", 1),
                        Map.of("name", "หวานมาก", "extraPrice", 5, "available", true, "sortOrder", 2))));
        assertThat(group.status()).isEqualTo(201);
        assertThat(group.data().get("defaultSelected").asBoolean()).isTrue();
        long groupId = group.data().get("id").asLong();

        assertThat(api.put("/api/v1/admin/products/" + productId + "/option-groups", api.adminToken(),
                List.of(Map.of("optionGroupId", groupId, "sortOrder", 1))).status()).isEqualTo(200);

        JsonNode detail = publicProduct(productId);
        assertThat(detail.get("optionGroups").get(0).get("items").size()).isEqualTo(2);
    }

    @Test
    void uploadAcceptsOnlyRealImagesAndServesThem() {
        Res ok = upload("/api/v1/files?folder=PRODUCTS", api.adminToken(), "a.png", "image/png", PNG);
        assertThat(ok.status()).isEqualTo(200);
        String url = ok.data().get("url").asString();
        assertThat(url).matches("/files/products/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");

        var served = api.get(url, null).raw().getResponse();
        assertThat(served.getStatus()).isEqualTo(200);
        assertThat(served.getContentType()).isEqualTo("image/png");
        assertThat(served.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");

        // HTML ปลอมนามสกุล .png → ปฏิเสธ (ตรวจ magic bytes)
        Res fake = upload("/api/v1/files", api.adminToken(), "evil.png", "image/png",
                "<script>alert(1)</script>".getBytes());
        assertThat(fake.errorCode()).isEqualTo("FILE_INVALID");
        // SVG ไม่รองรับ (XSS)
        Res svg = upload("/api/v1/files", api.adminToken(), "x.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes());
        assertThat(svg.errorCode()).isEqualTo("FILE_INVALID");
        // ลูกค้าอัปโหลดผ่าน /files ไม่ได้
        assertThat(upload("/api/v1/files", api.newCustomer().accessToken(), "a.png", "image/png", PNG).status()).isEqualTo(403);
        // โฟลเดอร์ที่ไม่มี (SLIPS ถูกเอาออกแล้ว) → 400
        assertThat(upload("/api/v1/files?folder=SLIPS", api.adminToken(), "a.png", "image/png", PNG).status()).isEqualTo(400);
    }

    @Test
    void fileServingBlocksTraversalAndPrivateFolders() {
        assertThat(api.get("/files/../application.yml", null).status()).isIn(400, 404);
        assertThat(api.get("/files/products/2026/09/..%2F..%2Fsecret.png", null).status()).isIn(400, 404);
        assertThat(api.get("/files/slips/2026/09/00000000-0000-0000-0000-000000000000.png", null).status()).isEqualTo(404);
    }

    @Test
    void externalImageUrlRejected() {
        long cat = createCategory("test-img");
        var p = product(cat, "TST-IMG", 10);
        p.put("imageUrl", "javascript:alert(1)");
        assertThat(api.post("/api/v1/admin/products", api.adminToken(), p).errorCode()).isEqualTo("VALIDATION_ERROR");
        p.put("imageUrl", "https://evil.example/x.png");
        assertThat(api.post("/api/v1/admin/products", api.adminToken(), p).errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void customerAvatarUpload() {
        var c = api.newCustomer();
        Res r = upload("/api/v1/me/profile/avatar", c.accessToken(), "me.png", "image/png", PNG);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.data().get("avatarUrl").asString()).startsWith("/files/avatars/");
    }

    private JsonNode publicProduct(long id) {
        Res r = api.get("/api/v1/public/products/" + id, null);
        assertThat(r.status()).isEqualTo(200);
        return r.data();
    }
}
