package com.roti5dao.user.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.security.PasswordPolicy;
import com.roti5dao.common.util.PhoneUtils;
import com.roti5dao.user.dto.UserDtos.MeResponse;
import com.roti5dao.user.entity.AppUser;
import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.repository.AppUserRepository;
import com.roti5dao.user.repository.CustomerProfileRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** บริการบัญชีผู้ใช้ที่ module อื่น (auth, order, point) เรียกใช้ได้ */
@Service
public class UserAccountService {

    private final AppUserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(AppUserRepository userRepository, CustomerProfileRepository profileRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record NewUser(String phone, String email, String rawPassword, String nickname, Role role,
                          boolean passwordChangeRequired) {
    }

    /** สร้างบัญชี + customer_profile (ทุก role มี profile เพื่อสะสมแต้มได้) */
    @Transactional
    public AppUser createUser(NewUser cmd) {
        String phone = requirePhone(cmd.phone());
        String email = normalizeEmail(cmd.email());
        PasswordPolicy.validate(cmd.rawPassword(), "password");
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_USED);
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
        }
        AppUser user = new AppUser();
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(cmd.rawPassword()));
        user.setRole(cmd.role());
        user.setPasswordChangeRequired(cmd.passwordChangeRequired());
        userRepository.saveAndFlush(user);

        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setNickname(cmd.nickname() == null ? null : cmd.nickname().trim());
        profileRepository.saveAndFlush(profile);
        return user;
    }

    /** หา user จากเบอร์โทรหรืออีเมล (ใช้ตอน login) */
    @Transactional(readOnly = true)
    public Optional<AppUser> findByUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 150) {
            return Optional.empty();
        }
        String u = username.trim();
        if (u.contains("@")) {
            return userRepository.findByEmailIgnoreCase(u);
        }
        String phone = PhoneUtils.normalize(u);
        return phone == null ? Optional.empty() : userRepository.findByPhone(phone);
    }

    @Transactional(readOnly = true)
    public AppUser getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("ผู้ใช้"));
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        AppUser user = getUser(userId);
        return MeResponse.of(user, profileRepository.findById(userId).orElse(null));
    }

    /** ใช้ที่ POS: หา customer จากเบอร์ — คืน userId ของสมาชิกที่ ACTIVE */
    @Transactional(readOnly = true)
    public Optional<Long> findActiveMemberIdByPhone(String rawPhone) {
        String phone = PhoneUtils.normalize(rawPhone);
        if (phone == null) {
            return Optional.empty();
        }
        return userRepository.findByPhone(phone).filter(AppUser::isActive).map(AppUser::getId);
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(Long userId) {
        return userId != null && userRepository.findById(userId).map(AppUser::isActive).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<String> findNickname(Long userId) {
        return profileRepository.findById(userId).map(CustomerProfile::getNickname);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findNicknames(Collection<Long> userIds) {
        // HashMap (ไม่ใช่ Map.of) เพราะผู้เรียก get(null) สำหรับออเดอร์ guest
        Map<Long, String> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        profileRepository.findAllById(userIds).forEach(p ->
                result.put(p.getUserId(), p.getNickname() != null ? p.getNickname() : p.getMemberCode()));
        return result;
    }

    public static String requirePhone(String raw) {
        String phone = PhoneUtils.normalize(raw);
        if (phone == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "เบอร์โทรไม่ถูกต้อง");
        }
        return phone;
    }

    public static String normalizeEmail(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
