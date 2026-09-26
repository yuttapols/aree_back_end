package com.roti5dao.common.security;

import com.roti5dao.common.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] API_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, JwtToAuthUserConverter converter,
                                            JsonSecurityHandlers handlers, RateLimitFilter rateLimitFilter,
                                            CookieOriginCheckFilter originCheckFilter,
                                            PasswordChangeRequiredFilter passwordChangeRequiredFilter) throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher apiDocs = new OrRequestMatcher(List.of(paths.matcher(API_DOCS[0]), paths.matcher(API_DOCS[1]), paths.matcher(API_DOCS[2])));

        http
                // API แบบ stateless ใช้ Bearer token (ไม่ได้อ่านจาก cookie) จึงไม่เสี่ยง CSRF
                // ส่วน refresh cookie ป้องกันด้วย SameSite=Strict + CookieOriginCheckFilter
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(apiDocs),
                                new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-site")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/me/**").authenticated()
                        // ADMIN-only ตรวจที่ระดับ URL ก่อน (ก่อน parse/validate body) — @PreAuthorize เป็นชั้นที่สอง
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/dashboard/today").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/v1/admin/staff/**", "/api/v1/admin/settings/**",
                                "/api/v1/admin/categories/**", "/api/v1/admin/option-groups/**",
                                "/api/v1/admin/promotions/**", "/api/v1/admin/dashboard/**",
                                "/api/v1/admin/customers/*/points/adjust").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/products", "/api/v1/admin/products/*/images",
                                "/api/v1/admin/payment-methods").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/products/**", "/api/v1/admin/payment-methods/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/products/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(j -> j.decoder(jwtService.decoder()).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(originCheckFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** ไม่อ่าน Bearer ที่ /auth/** และ /files/** — กัน access token หมดอายุไปบล็อกการ refresh / โหลดรูป */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> {
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/v1/auth/") || uri.startsWith("/files/")) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("Retry-After"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder(AppProperties props) {
        return new BCryptPasswordEncoder(props.security().password().bcryptStrength());
    }

    // filter เหล่านี้เป็น @Component จึงต้องปิด auto-registration ใน servlet container
    // (ให้ทำงานเฉพาะใน security filter chain เท่านั้น ไม่ซ้ำสองรอบ)
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    FilterRegistrationBean<CookieOriginCheckFilter> originCheckFilterRegistration(CookieOriginCheckFilter filter) {
        FilterRegistrationBean<CookieOriginCheckFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    FilterRegistrationBean<PasswordChangeRequiredFilter> passwordChangeFilterRegistration(PasswordChangeRequiredFilter filter) {
        FilterRegistrationBean<PasswordChangeRequiredFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
