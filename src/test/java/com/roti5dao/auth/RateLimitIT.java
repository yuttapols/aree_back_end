package com.roti5dao.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.support.IntegrationTest;
import com.roti5dao.support.TestApi.Res;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.security.rate-limit.enabled=true")
class RateLimitIT extends IntegrationTest {

    @Test
    void loginIsRateLimitedPerIp() {
        // @BeforeEach login ของ admin ใช้ไป 1–2 ครั้งแล้ว → เรียกจนกว่าจะโดนจำกัด (ไม่เกิน 10/นาที)
        Res last = null;
        int attempts = 0;
        for (int i = 0; i < 12; i++) {
            last = api.login("0899999998", "Wrong1234");
            attempts++;
            if (last.status() == 429) {
                break;
            }
        }
        assertThat(last.status()).isEqualTo(429);
        assertThat(attempts).isLessThanOrEqualTo(10);
        assertThat(last.errorCode()).isEqualTo("RATE_LIMITED");
        assertThat(last.raw().getResponse().getHeader("Retry-After")).isNotBlank();

        // endpoint อื่นไม่ได้รับผลกระทบ
        assertThat(api.get("/api/v1/public/menu", null).status()).isEqualTo(200);
    }
}
