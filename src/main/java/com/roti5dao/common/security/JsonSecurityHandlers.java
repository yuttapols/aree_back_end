package com.roti5dao.common.security;

import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** ตอบ 401/403 เป็น ApiResponse JSON (ไม่ใช่หน้า HTML / basic auth prompt) */
@Component
public class JsonSecurityHandlers {

    private final JsonMapper jsonMapper;

    public JsonSecurityHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            ErrorCode code = isExpired(ex) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, code);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorCode.FORBIDDEN);
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(code));
    }

    private static boolean isExpired(AuthenticationException ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof JwtValidationException jve) {
                return jve.getErrors().stream().anyMatch(e -> JwtService.EXPIRED_MARKER.equals(e.getDescription()));
            }
            t = t.getCause();
        }
        return false;
    }
}
