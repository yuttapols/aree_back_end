package com.roti5dao.common.security;

import com.roti5dao.user.entity.Role;

/** principal ใน SecurityContext หลังตรวจ JWT แล้ว */
public record AuthUser(Long id, Role role) {

    public boolean isStaffOrAdmin() {
        return role == Role.STAFF || role == Role.ADMIN;
    }
}
