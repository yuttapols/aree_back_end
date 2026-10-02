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

    /** id ของผู้ใช้ที่ login อยู่ หรือ null ถ้าเป็น guest (endpoint public ที่แนบ token ได้แต่ไม่บังคับ) */
    public static Long idOrNull() {
        return get().map(AuthUser::id).orElse(null);
    }

    /** ชื่อผู้กระทำสำหรับ audit/created_by: "user:{id}" หรือ fallback เมื่อไม่มีผู้ใช้ (job/guest) */
    public static String actorOr(String fallback) {
        return get().map(u -> actorOf(u.id())).orElse(fallback);
    }

    public static String actorOf(Long userId) {
        return "user:" + userId;
    }
}
