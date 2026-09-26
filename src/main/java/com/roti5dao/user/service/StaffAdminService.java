package com.roti5dao.user.service;

import com.roti5dao.auth.service.SessionRevocationService;
import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.security.PasswordPolicy;
import com.roti5dao.user.dto.UserDtos.StaffCreateRequest;
import com.roti5dao.user.dto.UserDtos.StaffResponse;
import com.roti5dao.user.dto.UserDtos.StaffUpdateRequest;
import com.roti5dao.user.entity.AppUser;
import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.entity.UserStatus;
import com.roti5dao.user.repository.AppUserRepository;
import com.roti5dao.user.repository.CustomerProfileRepository;
import com.roti5dao.user.service.UserAccountService.NewUser;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAdminService {

    private static final Set<Role> STAFF_ROLES = Set.of(Role.STAFF, Role.ADMIN);

    private final AppUserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final UserAccountService userAccountService;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;

    public StaffAdminService(AppUserRepository userRepository, CustomerProfileRepository profileRepository,
                             UserAccountService userAccountService, SessionRevocationService sessionRevocationService,
                             PasswordEncoder passwordEncoder, SecurityAuditService audit) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userAccountService = userAccountService;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list() {
        return userRepository.findByRoleInOrderByIdAsc(STAFF_ROLES).stream()
                .map(u -> StaffResponse.of(u, profileRepository.findById(u.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse get(Long id) {
        AppUser u = staff(id);
        return StaffResponse.of(u, profileRepository.findById(id).orElse(null));
    }

    @Transactional
    public StaffResponse create(StaffCreateRequest req) {
        requireStaffRole(req.role());
        AppUser u = userAccountService.createUser(new NewUser(req.phone(), req.email(), req.password(), req.nickname(),
                req.role(), true));
        audit.record(Event.USER_CREATED, u.getId(), "role=" + req.role());
        return StaffResponse.of(u, profileRepository.findById(u.getId()).orElse(null));
    }

    @Transactional
    public StaffResponse update(Long id, StaffUpdateRequest req, Long actorId) {
        requireStaffRole(req.role());
        AppUser u = staff(id);
        boolean roleChanged = u.getRole() != req.role();
        boolean suspended = u.getStatus() == UserStatus.ACTIVE && req.status() == UserStatus.SUSPENDED;
        boolean passwordChanged = req.newPassword() != null && !req.newPassword().isBlank();

        if (id.equals(actorId) && (roleChanged || req.status() != UserStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "ไม่สามารถลดสิทธิ์หรือระงับบัญชีของตัวเองได้");
        }
        boolean losingAdmin = u.getRole() == Role.ADMIN && u.isActive() && (req.role() != Role.ADMIN || req.status() != UserStatus.ACTIVE);
        if (losingAdmin && userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "ต้องมี ADMIN ที่ใช้งานได้อย่างน้อย 1 คน");
        }

        String email = UserAccountService.normalizeEmail(req.email());
        if (!Objects.equals(email, u.getEmail())) {
            if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
            }
            u.setEmail(email);
        }
        u.setRole(req.role());
        u.setStatus(req.status());
        if (passwordChanged) {
            PasswordPolicy.validate(req.newPassword(), "newPassword");
            u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            u.setPasswordChangeRequired(true);
        }
        if (req.status() == UserStatus.ACTIVE) {
            u.setLockedUntil(null);
            u.setFailedLoginCount(0);
        }
        CustomerProfile p = profileRepository.findById(id).orElse(null);
        if (p != null) {
            p.setNickname(req.nickname().trim());
        }

        if (roleChanged || suspended || passwordChanged) {
            sessionRevocationService.revokeAllSessions(id);
        }
        if (roleChanged) {
            audit.record(Event.USER_ROLE_CHANGED, id, "role=" + req.role());
        }
        if (suspended) {
            audit.record(Event.USER_SUSPENDED, id, null);
        }
        audit.record(Event.USER_UPDATED, id, passwordChanged ? "password reset" : null);
        return StaffResponse.of(u, p);
    }

    private AppUser staff(Long id) {
        return userRepository.findById(id)
                .filter(u -> STAFF_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new NotFoundException("พนักงาน"));
    }

    private static void requireStaffRole(Role role) {
        if (!STAFF_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "role ต้องเป็น STAFF หรือ ADMIN");
        }
    }
}
