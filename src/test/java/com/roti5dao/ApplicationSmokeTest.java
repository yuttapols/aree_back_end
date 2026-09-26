package com.roti5dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** context ขึ้นได้ = Flyway V1–V9 + seed รันผ่าน และ Entity ตรงกับ schema (ddl-auto=validate) */
class ApplicationSmokeTest extends IntegrationTest {

    @Test
    void publicMenuServedFromDatabase() {
        var r = api.get("/api/v1/public/menu", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("success").asBoolean()).isTrue();
        assertThat(r.data().size()).isEqualTo(4);
        assertThat(r.data().get(0).get("products").size()).isGreaterThan(0);
    }

    @Test
    void shopInfoIsPublic() {
        var r = api.get("/api/v1/public/shop-info", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.data().get("name").asString()).isEqualTo("ร้านโรตี 5 ดาว");
    }
}
