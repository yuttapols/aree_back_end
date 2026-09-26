package com.roti5dao.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * ใช้ remoteAddr เท่านั้น — ถ้าอยู่หลัง proxy ให้เปิด server.forward-headers-strategy=native
     * (ไม่อ่าน X-Forwarded-For เองเพราะ client ปลอมได้)
     */
    public static String of(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return of(attrs.getRequest());
        }
        return null;
    }
}
