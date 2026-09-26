package com.roti5dao.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.util.ClientIp;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit ต่อ IP (Bucket4j, in-memory) สำหรับ endpoint ที่เสี่ยงถูก brute force / spam
 * หมายเหตุ: ถ้ารันหลาย instance ควรย้ายไปใช้ bucket4j + Redis หรือทำที่ reverse proxy
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, RequestMatcher matcher, int perMinute) {
    }

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    private final List<Rule> rules = List.of(
            new Rule("login", PATHS.matcher(HttpMethod.POST, "/api/v1/auth/login"), 10),
            new Rule("register", PATHS.matcher(HttpMethod.POST, "/api/v1/auth/register"), 5),
            new Rule("refresh", PATHS.matcher(HttpMethod.POST, "/api/v1/auth/refresh"), 30),
            new Rule("order-create", PATHS.matcher(HttpMethod.POST, "/api/v1/public/orders"), 10),
            new Rule("order-quote", PATHS.matcher(HttpMethod.POST, "/api/v1/public/orders/quote"), 60),
            new Rule("slip", PATHS.matcher(HttpMethod.POST, "/api/v1/public/orders/track/*/payments"), 10),
            new Rule("track", PATHS.matcher(HttpMethod.GET, "/api/v1/public/orders/track/**"), 60),
            new Rule("promo-validate", PATHS.matcher(HttpMethod.POST, "/api/v1/public/promotions/validate"), 30),
            new Rule("password", PATHS.matcher(HttpMethod.PUT, "/api/v1/me/password"), 5));

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    private final boolean enabled;
    private final JsonSecurityHandlers json;

    public RateLimitFilter(AppProperties props, JsonSecurityHandlers json) {
        this.enabled = props.security().rateLimit().enabled();
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (Rule rule : rules) {
            if (rule.matcher().matches(request)) {
                String key = rule.name() + "|" + ClientIp.of(request);
                Bucket bucket = buckets.get(key, k -> Bucket.builder()
                        .addLimit(l -> l.capacity(rule.perMinute()).refillGreedy(rule.perMinute(), Duration.ofMinutes(1)))
                        .build());
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (!probe.isConsumed()) {
                    long waitSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
                    response.setHeader("Retry-After", String.valueOf(waitSeconds));
                    json.write(response, ErrorCode.RATE_LIMITED);
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }

    /** สำหรับ test */
    public void reset() {
        buckets.invalidateAll();
    }
}
