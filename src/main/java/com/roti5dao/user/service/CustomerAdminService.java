package com.roti5dao.user.service;

import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.security.TokenHashing;
import com.roti5dao.common.util.LikeUtils;
import com.roti5dao.common.util.PhoneUtils;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.user.dto.UserDtos.CustomerSummary;
import com.roti5dao.user.dto.UserDtos.QuickRegisterRequest;
import com.roti5dao.user.dto.UserDtos.QuickRegisterResponse;
import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.repository.CustomerProfileRepository;
import com.roti5dao.user.service.UserAccountService.NewUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAdminService {

    private final CustomerProfileRepository profileRepository;
    private final UserAccountService userAccountService;

    public CustomerAdminService(CustomerProfileRepository profileRepository, UserAccountService userAccountService) {
        this.profileRepository = profileRepository;
        this.userAccountService = userAccountService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummary> search(String keyword, Pageable pageable) {
        return PageResponse.of(profileRepository.search(Role.CUSTOMER, LikeUtils.containsPattern(keyword), pageable),
                CustomerSummary::of);
    }

    @Transactional(readOnly = true)
    public CustomerSummary lookupByPhone(String rawPhone) {
        String phone = PhoneUtils.normalize(rawPhone);
        if (phone == null) {
            throw new NotFoundException("สมาชิก");
        }
        return profileRepository.findByPhone(phone).map(CustomerSummary::of)
                .orElseThrow(() -> new NotFoundException("สมาชิก"));
    }

    @Transactional(readOnly = true)
    public CustomerSummary get(Long userId) {
        CustomerProfile p = profileRepository.findById(userId).orElseThrow(() -> new NotFoundException("สมาชิก"));
        return CustomerSummary.of(p);
    }

    /** สมัครให้ลูกค้าหน้าร้าน — ถ้าไม่ได้ตั้งรหัสให้ ระบบสุ่มรหัสชั่วคราว และบังคับเปลี่ยนเมื่อ login ครั้งแรก */
    @Transactional
    public QuickRegisterResponse quickRegister(QuickRegisterRequest req) {
        boolean generated = req.password() == null || req.password().isBlank();
        String password = generated ? TokenHashing.temporaryPassword() : req.password();
        var user = userAccountService.createUser(new NewUser(req.phone(), null, password, req.nickname(), Role.CUSTOMER, true));
        CustomerProfile p = profileRepository.findById(user.getId()).orElseThrow();
        return new QuickRegisterResponse(CustomerSummary.of(p), generated ? password : null);
    }
}
