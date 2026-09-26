package com.roti5dao.common.security;

import com.roti5dao.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * บัญชีที่ต้องเปลี่ยนรหัสผ่าน (ADMIN คนแรก, พนักงานที่แอดมินสร้าง, ลูกค้าที่ได้รหัสชั่วคราวจากหน้าร้าน)
 * ใช้ได้เฉพาะ GET /me และ PUT /me/password จนกว่าจะเปลี่ยนรหัส — บังคับที่ backend ไม่ใช่แค่ FE
 */
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final UserSecurityStateService stateService;
    private final JsonSecurityHandlers json;

    public PasswordChangeRequiredFilter(UserSecurityStateService stateService, JsonSecurityHandlers json) {
        this.stateService = stateService;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var user = CurrentUser.get();
        if (user.isPresent() && !isAllowed(request)
                && stateService.get(user.get().id()).map(UserSecurityStateService.State::passwordChangeRequired).orElse(false)) {
            json.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAllowed(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return uri.startsWith("/api/v1/auth/")
                || ("GET".equals(method) && "/api/v1/me".equals(uri))
                || ("PUT".equals(method) && "/api/v1/me/password".equals(uri));
    }
}
