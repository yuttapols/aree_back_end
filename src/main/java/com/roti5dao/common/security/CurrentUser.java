package com.roti5dao.common.security;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** ดึงผู้ใช้ปัจจุบันจาก SecurityContext */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser require() {
        return get().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public static Long requireId() {
        return require().id();
    }
}
