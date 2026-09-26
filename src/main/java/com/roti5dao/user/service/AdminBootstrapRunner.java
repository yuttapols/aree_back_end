package com.roti5dao.user.service;

import com.roti5dao.common.config.AppProperties;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.repository.AppUserRepository;
import com.roti5dao.user.service.UserAccountService.NewUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * สร้าง ADMIN คนแรกจาก env ROTI_ADMIN_PHONE / ROTI_ADMIN_PASSWORD เมื่อระบบยังไม่มี ADMIN
 * (ไม่ seed รหัสผ่านใน migration/git) — ADMIN ที่สร้างจะถูกบังคับเปลี่ยนรหัสผ่านเมื่อ login ครั้งแรก
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AppUserRepository userRepository;
    private final UserAccountService userAccountService;
    private final AppProperties.Bootstrap props;

    public AdminBootstrapRunner(AppUserRepository userRepository, UserAccountService userAccountService, AppProperties props) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.props = props.bootstrap();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (isBlank(props.adminPhone()) || isBlank(props.adminPassword())) {
            log.warn("No ADMIN account exists. Set ROTI_ADMIN_PHONE and ROTI_ADMIN_PASSWORD to bootstrap the first admin.");
            return;
        }
        var admin = userAccountService.createUser(new NewUser(props.adminPhone(), null, props.adminPassword(),
                isBlank(props.adminNickname()) ? "Admin" : props.adminNickname(), Role.ADMIN, true));
        log.info("Bootstrapped first ADMIN account (id={}). Password change is required on first login.", admin.getId());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
