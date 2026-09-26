package com.roti5dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Swagger / OpenAPI ต้องเปิดดูได้ และ FE ใช้ /v3/api-docs generate client ได้ */
class OpenApiIT extends IntegrationTest {

    private JsonNode docs() {
        var r = api.get("/v3/api-docs", null);
        assertThat(r.status()).isEqualTo(200);
        return r.body();
    }

    @Test
    void apiDocsCoverAllModules() throws Exception {
        JsonNode d = docs();
        // export ให้ FE generate client ได้โดยไม่ต้องรัน server: target/openapi.json
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "openapi.json"),
                api.json().writerWithDefaultPrettyPrinter().writeValueAsString(d));
        assertThat(d.get("openapi").asString()).startsWith("3.");
        assertThat(d.get("info").get("description").asString()).contains("POINT_INSUFFICIENT").contains("TOKEN_EXPIRED");
        JsonNode paths = d.get("paths");
        for (String p : new String[]{"/api/v1/auth/login", "/api/v1/me", "/api/v1/public/menu", "/api/v1/public/orders",
                "/api/v1/admin/orders", "/api/v1/admin/payments/{id}/verify", "/api/v1/me/points",
                "/api/v1/admin/promotions", "/api/v1/admin/dashboard/summary", "/api/v1/files"}) {
            assertThat(paths.has(p)).as(p).isTrue();
        }
        // ทุก operation มี summary + tag
        paths.properties().forEach(e -> e.getValue().properties().forEach(op -> {
            assertThat(op.getValue().has("summary")).as(e.getKey() + " " + op.getKey()).isTrue();
            assertThat(op.getValue().get("tags").size()).as(e.getKey()).isGreaterThan(0);
        }));
    }

    @Test
    void securityDescribedPerEndpoint() {
        JsonNode paths = docs().get("paths");
        assertThat(paths.get("/api/v1/auth/login").get("post").get("security").size()).isZero();
        assertThat(paths.get("/api/v1/auth/refresh").get("post").get("security").get(0).has("refreshCookie")).isTrue();
        // public: แนบ token ได้แต่ไม่บังคับ
        JsonNode publicSec = paths.get("/api/v1/public/orders").get("post").get("security");
        assertThat(publicSec.size()).isEqualTo(2);
        // endpoint ที่ต้อง login ใช้ bearer (global) และมี response 401/403
        JsonNode me = paths.get("/api/v1/me").get("get");
        assertThat(me.has("security")).isFalse();
        assertThat(me.get("responses").has("401")).isTrue();
        assertThat(me.get("responses").has("403")).isTrue();
    }

    @Test
    void swaggerUiServed() {
        var r = api.get("/swagger-ui/index.html", null).raw().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        // swagger-ui ต้องไม่โดน CSP default-src 'none' ของ API
        assertThat(r.getHeader("Content-Security-Policy")).isNull();
    }
}
