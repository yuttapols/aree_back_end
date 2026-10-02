package com.roti5dao.point.service;

import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.point.dto.PointDtos.PointSummary;
import com.roti5dao.point.dto.PointDtos.PointTransactionResponse;
import com.roti5dao.point.entity.PointTransaction;
import com.roti5dao.point.entity.PointType;
import com.roti5dao.point.repository.PointTransactionRepository;
import com.roti5dao.user.service.CustomerBalanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point engine — ทุกการเปลี่ยนแปลงแต้มเขียน ledger (point_transaction) + อัปเดต points_balance ใน transaction เดียวกัน
 * lock แถวลูกค้าก่อนเสมอ (CustomerBalanceService.lock) แล้วจึง lock ก้อนแต้ม — ลำดับเดียวกันทุกที่ กัน deadlock
 */
@Service
public class PointService {

    public static final int EXPIRING_WINDOW_DAYS = 30;

    private final PointTransactionRepository repository;
    private final CustomerBalanceService balanceService;
    private final SystemSettingService settings;
    private final SecurityAuditService audit;
    private final Clock clock;

    public PointService(PointTransactionRepository repository, CustomerBalanceService balanceService,
                        SystemSettingService settings, SecurityAuditService audit, Clock clock) {
        this.repository = repository;
        this.balanceService = balanceService;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ preview (ไม่มี side effect)

    public record RedeemPreview(int pointsUsed, BigDecimal discount) {
    }

    /** ตรวจกติกาการแลกแต้ม — ใช้ใน PointRedeemStep (quote และ create) */
    @Transactional(readOnly = true)
    public RedeemPreview previewRedeem(Long customerId, int requestedPoints, BigDecimal amountAfterPromotion) {
        if (customerId == null) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT, "ต้องเป็นสมาชิกจึงแลกแต้มได้");
        }
        int min = settings.getInt(SettingKey.POINT_REDEEM_MIN_POINTS);
        if (requestedPoints < min) {
            throw new BusinessException(ErrorCode.POINT_BELOW_MIN, "แลกแต้มขั้นต่ำ " + min + " แต้ม");
        }
        if (balanceService.balance(customerId) < requestedPoints) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
        int rate = settings.getInt(SettingKey.POINT_REDEEM_POINTS_PER_BAHT);
        int discountBaht = requestedPoints / rate;
        int maxPercent = settings.getInt(SettingKey.POINT_REDEEM_MAX_PERCENT);
        BigDecimal maxDiscount = amountAfterPromotion.multiply(BigDecimal.valueOf(maxPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        if (BigDecimal.valueOf(discountBaht).compareTo(maxDiscount) > 0) {
            throw new BusinessException(ErrorCode.POINT_EXCEED_LIMIT,
                    "ใช้แต้มลดได้ไม่เกิน " + maxPercent + "% ของยอด (สูงสุด " + maxDiscount + " บาท)");
        }
        return new RedeemPreview(discountBaht * rate, BigDecimal.valueOf(discountBaht).setScale(2, RoundingMode.UNNECESSARY));
    }

    /** แต้มที่จะได้จากยอดสุทธิ (ปัดลง) × ตัวคูณโปร */
    public int calculateEarn(BigDecimal total, BigDecimal multiplier) {
        int bahtPerPoint = settings.getInt(SettingKey.POINT_EARN_BAHT_PER_POINT);
        int base = total.divide(BigDecimal.valueOf(bahtPerPoint), 0, RoundingMode.DOWN).intValue();
        return BigDecimal.valueOf(base).multiply(multiplier == null ? BigDecimal.ONE : multiplier)
                .setScale(0, RoundingMode.DOWN).intValue();
    }

    // ------------------------------------------------------------------ ledger operations

    /** ตัดแต้มที่แลก ตอนสร้างออเดอร์ (FIFO ตามวันหมดอายุ) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(Long customerId, Long orderId, int points) {
        if (points <= 0) {
            return;
        }
        Instant now = Instant.now(clock);
        balanceService.lock(customerId);
        expireLots(customerId, now);
        consumeFifo(customerId, points, now, null);
        int balance = balanceService.apply(customerId, -points, false);
        record(customerId, orderId, PointType.REDEEM, -points, balance, null, null, "แลกแต้มเป็นส่วนลด");
    }

    /** ให้แต้มเมื่อออเดอร์ COMPLETED — idempotent (1 ออเดอร์ได้ EARN ครั้งเดียว) */
    @Transactional(propagation = Propagation.MANDATORY)
    public int earn(Long customerId, Long orderId, BigDecimal total, BigDecimal multiplier) {
        balanceService.lock(customerId);
        var existing = repository.findFirstByOrderIdAndType(orderId, PointType.EARN);
        if (existing.isPresent()) {
            return existing.get().getPoints();
        }
        int points = calculateEarn(total, multiplier);
        if (points <= 0) {
            return 0;
        }
        int balance = balanceService.apply(customerId, points, true);
        String remark = multiplier != null && multiplier.compareTo(BigDecimal.ONE) > 0
                ? "ได้แต้มจากออเดอร์ (x" + multiplier.stripTrailingZeros().toPlainString() + ")" : "ได้แต้มจากออเดอร์";
        record(customerId, orderId, PointType.EARN, points, balance, points, expiry(), remark);
        return points;
    }

    /** ยกเลิกออเดอร์: คืนแต้มที่แลก + ดึงแต้มที่ได้คืน (ไม่เกินยอดคงเหลือ) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(Long customerId, Long orderId) {
        if (repository.existsByOrderIdAndType(orderId, PointType.REVERSE)) {
            return;
        }
        Instant now = Instant.now(clock);
        balanceService.lock(customerId);

        int redeemed = -repository.sumByOrderAndType(orderId, PointType.REDEEM);
        if (redeemed > 0) {
            int balance = balanceService.apply(customerId, redeemed, false);
            record(customerId, orderId, PointType.REVERSE, redeemed, balance, redeemed, expiry(), "คืนแต้มจากการยกเลิกออเดอร์");
        }

        var earnLot = repository.findFirstByOrderIdAndType(orderId, PointType.EARN);
        if (earnLot.isPresent()) {
            expireLots(customerId, now);
            int clawback = Math.min(earnLot.get().getPoints(), balanceService.lock(customerId));
            if (clawback > 0) {
                consumeFifo(customerId, clawback, now, earnLot.get());
                int balance = balanceService.apply(customerId, -clawback, false);
                record(customerId, orderId, PointType.REVERSE, -clawback, balance, null, null, "ดึงแต้มคืนจากการยกเลิกออเดอร์");
            }
        }
    }

    /** แอดมินปรับแต้ม (+/−) พร้อมเหตุผล */
    @Transactional
    public PointTransactionResponse adjust(Long customerId, int points, String remark) {
        if (points == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "จำนวนแต้มต้องไม่เป็น 0");
        }
        Instant now = Instant.now(clock);
        balanceService.lock(customerId);
        PointTransaction tx;
        if (points > 0) {
            int balance = balanceService.apply(customerId, points, true);
            tx = record(customerId, null, PointType.ADJUST, points, balance, points, expiry(), remark.trim());
        } else {
            expireLots(customerId, now);
            consumeFifo(customerId, -points, now, null);
            int balance = balanceService.apply(customerId, points, false);
            tx = record(customerId, null, PointType.ADJUST, points, balance, null, null, remark.trim());
        }
        audit.record(Event.POINT_ADJUSTED, customerId, "points=" + points);
        return PointTransactionResponse.of(tx);
    }

    /** ตัดแต้มหมดอายุของลูกค้า 1 คน (เรียกจาก job) */
    @Transactional
    public int expireForCustomer(Long customerId) {
        balanceService.lock(customerId);
        return expireLots(customerId, Instant.now(clock));
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PointSummary summary(Long customerId) {
        Instant now = Instant.now(clock);
        return new PointSummary(balanceService.balance(customerId),
                repository.sumExpiringBetween(customerId, now, now.plus(Duration.ofDays(EXPIRING_WINDOW_DAYS))),
                repository.nextExpiry(customerId, now), EXPIRING_WINDOW_DAYS);
    }

    @Transactional(readOnly = true)
    public PageResponse<PointTransactionResponse> transactions(Long customerId, Pageable pageable) {
        return PageResponse.of(repository.findByCustomerId(customerId, pageable), PointTransactionResponse::of);
    }

    /** ใช้ตรวจ invariant: ผลรวม ledger ต้องเท่ากับ points_balance เสมอ */
    @Transactional(readOnly = true)
    public boolean isLedgerConsistent(Long customerId) {
        return repository.sumLedger(customerId) == balanceService.balance(customerId);
    }

    @Transactional(readOnly = true)
    public List<Long> customersWithExpiredLots(int limit) {
        return repository.findCustomersWithExpiredLots(Instant.now(clock), org.springframework.data.domain.PageRequest.of(0, limit));
    }

    // ------------------------------------------------------------------ internals

    private int expireLots(Long customerId, Instant now) {
        int total = 0;
        for (PointTransaction lot : repository.findExpiredLotsForUpdate(customerId, now)) {
            int amount = lot.getRemaining();
            lot.setRemaining(0);
            int balance = balanceService.apply(customerId, -amount, false);
            record(customerId, null, PointType.EXPIRE, -amount, balance, null, null, "แต้มหมดอายุ (รายการ #" + lot.getId() + ")");
            total += amount;
        }
        return total;
    }

    /** ตัดแต้มจากก้อนที่ใกล้หมดอายุก่อน — preferred = ก้อนที่ต้องตัดก่อน (เช่น ก้อน EARN ของออเดอร์ที่ถูกยกเลิก) */
    private void consumeFifo(Long customerId, int points, Instant now, PointTransaction preferred) {
        int left = points;
        List<PointTransaction> lots = repository.findUsableLotsForUpdate(customerId, now);
        if (preferred != null) {
            for (PointTransaction lot : lots) {
                if (lot.getId().equals(preferred.getId())) {
                    left -= take(lot, left);
                }
            }
        }
        for (PointTransaction lot : lots) {
            if (left == 0) {
                break;
            }
            left -= take(lot, left);
        }
        if (left > 0) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }
    }

    private static int take(PointTransaction lot, int wanted) {
        int take = Math.min(wanted, lot.getRemaining());
        lot.setRemaining(lot.getRemaining() - take);
        return take;
    }

    private Instant expiry() {
        return Instant.now(clock).plus(Duration.ofDays(settings.getInt(SettingKey.POINT_EXPIRE_DAYS)));
    }

    private PointTransaction record(Long customerId, Long orderId, PointType type, int points, int balanceAfter,
                                    Integer remaining, Instant expiresAt, String remark) {
        PointTransaction t = new PointTransaction();
        t.setCustomerId(customerId);
        t.setOrderId(orderId);
        t.setType(type);
        t.setPoints(points);
        t.setBalanceAfter(balanceAfter);
        t.setRemaining(remaining);
        t.setExpiresAt(expiresAt);
        t.setRemark(remark);
        t.setCreatedAt(Instant.now(clock));
        t.setCreatedBy(CurrentUser.actorOr("system"));
        return repository.save(t);
    }
}
