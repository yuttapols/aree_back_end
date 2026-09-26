package com.roti5dao.user.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ยอดแต้มคงเหลือ (customer_profile.points_balance เป็น cache ของ ledger)
 * — module point เรียกใช้ภายใน transaction เดียวกับการเขียน point_transaction
 * — lock แถว customer_profile (SELECT ... FOR UPDATE) ป้องกันแต้มเพี้ยนเมื่อทำรายการพร้อมกัน
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CustomerBalanceService {

    private final CustomerProfileRepository repository;

    public CustomerBalanceService(CustomerProfileRepository repository) {
        this.repository = repository;
    }

    /** lock แถวลูกค้าและคืนยอดคงเหลือปัจจุบัน */
    public int lock(Long customerId) {
        return profile(customerId).getPointsBalance();
    }

    /** ปรับยอด (+/-) คืนยอดใหม่ — ติดลบไม่ได้ */
    public int apply(Long customerId, int delta, boolean countLifetime) {
        CustomerProfile p = profile(customerId);
        int next = p.getPointsBalance() + delta;
        if (next < 0) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
        p.setPointsBalance(next);
        if (countLifetime && delta > 0) {
            p.setLifetimePoints(p.getLifetimePoints() + delta);
        }
        return next;
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public int balance(Long customerId) {
        return repository.findById(customerId).map(CustomerProfile::getPointsBalance).orElse(0);
    }

    private CustomerProfile profile(Long customerId) {
        return repository.findForUpdate(customerId).orElseThrow(() -> new NotFoundException("สมาชิก"));
    }
}
