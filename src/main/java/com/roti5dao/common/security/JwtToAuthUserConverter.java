package com.roti5dao.common.security;

import com.roti5dao.user.entity.Role;
import java.time.Instant;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * แปลง JWT ที่ลายเซ็นถูกต้องแล้ว เป็น {@link AuthUser}
 * และตรวจกับ DB (ผ่าน cache) ว่าบัญชียัง ACTIVE, role ไม่เปลี่ยน, และ token ไม่ได้ออกก่อนถูก revoke
 */
@Component
public class JwtToAuthUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserSecurityStateService stateService;

    public JwtToAuthUserConverter(UserSecurityStateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        String role = jwt.getClaimAsString(JwtService.CLAIM_ROLE);
        var state = stateService.get(userId).orElseThrow(() -> new InvalidBearerTokenException("user not found"));
        if (!state.active() || !state.role().equals(role)) {
            throw new InvalidBearerTokenException("user state changed");
        }
        Instant iat = jwt.getIssuedAt();
        if (state.tokensValidAfter() != null && iat != null
                && iat.getEpochSecond() < state.tokensValidAfter().getEpochSecond()) {
            throw new InvalidBearerTokenException("token revoked");
        }
        AuthUser principal = new AuthUser(userId, Role.valueOf(role));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
