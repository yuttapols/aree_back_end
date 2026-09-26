package com.roti5dao.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test บน PostgreSQL จริง (zonky embedded-postgres — ไม่ต้องใช้ Docker)
 * รัน Flyway migration ทั้งหมด + Hibernate ddl-auto=validate จึงตรวจได้ว่า Entity ตรงกับ schema
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    /** เริ่มครั้งเดียวต่อ JVM ใช้ร่วมกันทุก test class */
    private static final EmbeddedPostgres POSTGRES = start();

    public static final Instant START = Instant.parse("2026-09-23T05:00:00Z"); // พุธ 12:00 เวลาไทย

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected TestApi api;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void resetClock() {
        clock.set(START);
        // เปลี่ยนรหัส ADMIN ครั้งแรก ณ เวลา START เสมอ (ถ้าไปเกิดตอน test เลื่อนนาฬิกาไปอนาคต
        // tokens_valid_after จะอยู่ในอนาคตและ token ที่ออก ณ START จะถูกมองว่าถูก revoke — ซึ่งถูกต้องตาม design)
        api.adminToken();
    }

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(TestApi.class)
    public static class ClockConfig {
        /** @Primary → ทุกจุดที่ inject {@link Clock} จะได้ clock ที่เลื่อนเวลาได้ */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }
}
