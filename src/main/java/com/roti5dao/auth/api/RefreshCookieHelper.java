package com.roti5dao.auth.api;

import com.roti5dao.auth.service.RefreshTokenService;
import com.roti5dao.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** refresh token cookie: HttpOnly + Secure + SameSite=Strict + path จำกัดที่ /api/v1/auth */
@Component
public class RefreshCookieHelper {

    private final AppProperties.RefreshCookie props;
    private final Clock clock;

    public RefreshCookieHelper(AppProperties appProperties, Clock clock) {
        this.props = appProperties.security().refreshCookie();
        this.clock = clock;
    }

    public ResponseCookie create(RefreshTokenService.Issued issued) {
        return base(issued.rawToken())
                .maxAge(Duration.between(Instant.now(clock), issued.expiresAt()))
                .build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (props.name().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(props.name(), value)
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(props.path());
    }
}
