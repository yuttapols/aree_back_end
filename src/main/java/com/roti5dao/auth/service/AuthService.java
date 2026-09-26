package com.roti5dao.auth.service;

import com.roti5dao.auth.dto.AuthDtos.LoginRequest;
import com.roti5dao.auth.dto.AuthDtos.RegisterRequest;
import com.roti5dao.auth.dto.AuthDtos.TokenResponse;
import com.roti5dao.auth.entity.RefreshToken;
import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.security.JwtService;
import com.roti5dao.common.security.PasswordPolicy;
import com.roti5dao.user.entity.AppUser;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.service.UserAccountService;
import com.roti5dao.user.service.UserAccountService.NewUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** ถ้า token ที่ถูก rotate แล้วถูกใช้ซ้ำภายในช่วงนี้ ถือว่าเป็น request ซ้อนกัน (หลายแท็บ) ไม่ใช่การขโมย */
    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;
    private final SessionRevocationService sessionRevocationService;
    private final LoginAttemptService loginAttemptService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserAccountService userAccountService, RefreshTokenService refreshTokenService,
                       SessionRevocationService sessionRevocationService, LoginAttemptService loginAttemptService,
                       JwtService jwtService, PasswordEncoder passwordEncoder, SecurityAuditService audit, Clock clock) {
        this.userAccountService = userAccountService;
        this.refreshTokenService = refreshTokenService;
        this.sessionRevocationService = sessionRevocationService;
        this.loginAttemptService = loginAttemptService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
        // ใช้เทียบเมื่อไม่พบ user เพื่อให้เวลาตอบสนองใกล้เคียงกัน (กันการเดาว่ามีบัญชีหรือไม่จากเวลา)
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record AuthResult(TokenResponse body, RefreshTokenService.Issued refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterRequest req, String userAgent) {
        AppUser user = userAccountService.createUser(new NewUser(req.phone(), req.email(), req.password(),
                req.nickname(), Role.CUSTOMER, false));
        audit.record(Event.REGISTER, user.getId(), null);
        return issueTokens(user, null, userAgent);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult login(LoginRequest req, String userAgent) {
        AppUser user = userAccountService.findByUsername(req.username()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            audit.record(Event.LOGIN_FAILED, null, "unknown username");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Instant now = Instant.now(clock);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            audit.record(Event.LOGIN_LOCKED, user.getId(), null);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            boolean locked = loginAttemptService.recordFailure(user.getId());
            audit.record(locked ? Event.LOGIN_LOCKED : Event.LOGIN_FAILED, user.getId(), null);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        loginAttemptService.recordSuccess(user.getId());
        audit.record(Event.LOGIN_SUCCESS, user.getId(), null);
        return issueTokens(user, null, userAgent);
    }

    /** rotate refresh token ทุกครั้ง + ตรวจจับการใช้ token เก่าซ้ำ (token theft) */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawRefreshToken, String userAgent) {
        RefreshToken token = refreshTokenService.findForUpdate(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Instant now = Instant.now(clock);
        if (token.getRevokedAt() != null) {
            if (token.getRevokedAt().isBefore(now.minus(REUSE_GRACE))) {
                // token ที่ถูกใช้ไปแล้วกลับมาอีก → น่าจะถูกขโมย: ยกเลิกทุก session ของผู้ใช้นี้
                refreshTokenService.revokeFamily(token.getFamilyId());
                sessionRevocationService.revokeAllSessions(token.getUserId());
                audit.record(Event.REFRESH_TOKEN_REUSE, token.getUserId(), "family=" + token.getFamilyId());
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AppUser user = userAccountService.getUser(token.getUserId());
        if (!user.isActive()) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        token.setRevokedAt(now);
        return issueTokens(user, token.getFamilyId(), userAgent);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.findForUpdate(rawRefreshToken).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now(clock));
            }
            audit.record(Event.LOGOUT, t.getUserId(), null);
        });
    }

    /** เปลี่ยนรหัสผ่าน → revoke ทุก session เดิม แล้วออก token ชุดใหม่ให้ session ปัจจุบัน */
    @Transactional
    public AuthResult changePassword(Long userId, String currentPassword, String newPassword, String userAgent) {
        AppUser user = userAccountService.getUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "รหัสผ่านปัจจุบันไม่ถูกต้อง");
        }
        PasswordPolicy.validate(newPassword, "newPassword");
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "รหัสผ่านใหม่ต้องไม่ซ้ำกับรหัสเดิม");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        sessionRevocationService.revokeAllSessions(userId);
        audit.record(Event.PASSWORD_CHANGED, userId, null);
        return issueTokens(user, null, userAgent);
    }

    private AuthResult issueTokens(AppUser user, UUID familyId, String userAgent) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user.getId(), user.getRole());
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user.getId(), familyId, userAgent);
        long expiresIn = Math.max(0, Duration.between(Instant.now(clock), access.expiresAt()).toSeconds());
        TokenResponse body = new TokenResponse(access.token(), "Bearer", expiresIn, access.expiresAt(),
                userAccountService.getMe(user.getId()));
        return new AuthResult(body, refresh);
    }
}
