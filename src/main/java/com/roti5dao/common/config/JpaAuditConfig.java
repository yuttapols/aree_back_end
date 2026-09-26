package com.roti5dao.common.config;

import com.roti5dao.common.security.AuthUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditDateTimeProvider")
public class JpaAuditConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
                return Optional.of("user:" + user.id());
            }
            return Optional.of("system");
        };
    }

    @Bean
    DateTimeProvider auditDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
