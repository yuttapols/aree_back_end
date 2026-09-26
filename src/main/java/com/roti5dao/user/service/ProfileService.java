package com.roti5dao.user.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.storage.FileStorageService;
import com.roti5dao.common.storage.StorageFolder;
import com.roti5dao.user.dto.UserDtos.ProfileResponse;
import com.roti5dao.user.dto.UserDtos.ProfileUpdateRequest;
import com.roti5dao.user.entity.AppUser;
import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.repository.AppUserRepository;
import com.roti5dao.user.repository.CustomerProfileRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {

    private final AppUserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final FileStorageService storage;

    public ProfileService(AppUserRepository userRepository, CustomerProfileRepository profileRepository,
                          FileStorageService storage) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        CustomerProfile p = profile(userId);
        return ProfileResponse.of(p.getUser(), p);
    }

    @Transactional
    public ProfileResponse update(Long userId, ProfileUpdateRequest req) {
        CustomerProfile p = profile(userId);
        AppUser u = p.getUser();
        String email = UserAccountService.normalizeEmail(req.email());
        if (!Objects.equals(email, u.getEmail())) {
            if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED);
            }
            u.setEmail(email);
        }
        p.setFirstName(trim(req.firstName()));
        p.setLastName(trim(req.lastName()));
        p.setNickname(trim(req.nickname()));
        p.setBirthDate(req.birthDate());
        p.setGender(req.gender());
        return ProfileResponse.of(u, p);
    }

    @Transactional
    public ProfileResponse updateAvatar(Long userId, MultipartFile file) {
        CustomerProfile p = profile(userId);
        var stored = storage.store(file, StorageFolder.AVATARS);
        p.setAvatarUrl(stored.location());
        return ProfileResponse.of(p.getUser(), p);
    }

    private CustomerProfile profile(Long userId) {
        return profileRepository.findById(userId).orElseThrow(() -> new NotFoundException("โปรไฟล์"));
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
