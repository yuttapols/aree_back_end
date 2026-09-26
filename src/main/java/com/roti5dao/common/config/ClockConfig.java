package com.roti5dao.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** ใช้ Clock bean แทน Instant.now() ตรงๆ เพื่อให้ทดสอบเรื่องเวลาได้ (โปรโมชั่น/แต้มหมดอายุ) */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
