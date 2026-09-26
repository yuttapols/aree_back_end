package com.roti5dao.common.security;

import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ป้องกัน CSRF สำหรับ endpoint ที่ใช้ refresh-token cookie (/api/v1/auth/refresh, /logout)
 * — ชั้นที่ 1: cookie เป็น SameSite=Strict
 * — ชั้นที่ 2 (filter นี้): ถ้า browser ส่ง Origin / Sec-Fetch-Site มา ต้องมาจาก origin ที่อนุญาตเท่านั้น
 */
@Component
public class CookieOriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_ENDPOINTS = Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final Set<String> allowedOrigins;
    private final JsonSecurityHandlers json;

    public CookieOriginCheckFilter(AppProperties props, JsonSecurityHandlers json) {
        this.allowedOrigins = Set.copyOf(props.cors().allowedOrigins());
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !COOKIE_ENDPOINTS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        boolean originOk = origin == null || allowedOrigins.contains(origin) || isSameOrigin(request, origin);
        boolean siteOk = fetchSite == null || !fetchSite.equalsIgnoreCase("cross-site") || (origin != null && allowedOrigins.contains(origin));
        if (!originOk || !siteOk) {
            json.write(response, ErrorCode.FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSameOrigin(HttpServletRequest request, String origin) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String self = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        return self.equalsIgnoreCase(origin);
    }
}
