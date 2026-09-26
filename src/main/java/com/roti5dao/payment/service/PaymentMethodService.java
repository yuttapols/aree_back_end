package com.roti5dao.payment.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.payment.dto.PaymentDtos.PaymentMethodResponse;
import com.roti5dao.payment.dto.PaymentDtos.PaymentMethodUpsertRequest;
import com.roti5dao.payment.dto.PaymentDtos.PublicPaymentMethod;
import com.roti5dao.payment.entity.PaymentMethod;
import com.roti5dao.payment.repository.PaymentMethodRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    private final PaymentMethodRepository repository;
    private final SystemSettingService settings;

    public PaymentMethodService(PaymentMethodRepository repository, SystemSettingService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /** channel=ONLINE → เฉพาะช่องทางที่ลูกค้าเลือกเองได้ (allow_online) */
    @Transactional(readOnly = true)
    public List<PublicPaymentMethod> publicList(boolean onlineOnly) {
        String promptPay = blankToNull(settings.getString(SettingKey.PAYMENT_PROMPTPAY_ID));
        String bank = blankToNull(settings.getString(SettingKey.PAYMENT_BANK_ACCOUNT));
        return repository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .filter(m -> !onlineOnly || m.isAllowOnline())
                .map(m -> new PublicPaymentMethod(m.getCode(), m.getName(), m.isRequiresSlip(), m.isRequiresReference(),
                        m.getInstruction(), m.getIcon(),
                        "PROMPTPAY".equals(m.getCode()) ? promptPay : null,
                        "TRANSFER".equals(m.getCode()) ? bank : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> adminList() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(PaymentMethodResponse::of).toList();
    }

    @Transactional
    public PaymentMethodResponse create(PaymentMethodUpsertRequest req) {
        if (repository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VALUE, "รหัสช่องทางชำระเงินซ้ำ");
        }
        PaymentMethod m = new PaymentMethod();
        m.setCode(req.code());
        apply(m, req);
        return PaymentMethodResponse.of(repository.saveAndFlush(m));
    }

    /** code แก้ไม่ได้ (ใช้อ้างอิงใน logic เช่น CASH) */
    @Transactional
    public PaymentMethodResponse update(Long id, PaymentMethodUpsertRequest req) {
        PaymentMethod m = repository.findById(id).orElseThrow(() -> new NotFoundException("ช่องทางชำระเงิน"));
        if (!m.getCode().equals(req.code())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ไม่สามารถแก้ไขรหัสช่องทางชำระเงินได้");
        }
        apply(m, req);
        return PaymentMethodResponse.of(repository.saveAndFlush(m));
    }

    /** หาช่องทางที่ใช้งานได้ — forOnline=true ต้องเป็นช่องทางที่ลูกค้าเลือกเองได้ด้วย */
    @Transactional(readOnly = true)
    public PaymentMethod requireUsable(String code, boolean forOnline) {
        PaymentMethod m = repository.findByCode(code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE));
        if (!m.isActive() || (forOnline && !m.isAllowOnline())) {
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE);
        }
        return m;
    }

    private static void apply(PaymentMethod m, PaymentMethodUpsertRequest req) {
        if (m.isCash() && (req.requiresSlip() || req.allowOnline())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "เงินสดต้องไม่บังคับสลิปและใช้ออนไลน์ไม่ได้");
        }
        m.setName(req.name().trim());
        m.setRequiresSlip(req.requiresSlip());
        m.setRequiresReference(req.requiresReference());
        m.setAllowOnline(req.allowOnline());
        m.setInstruction(req.instruction());
        m.setIcon(req.icon());
        m.setActive(req.active());
        m.setSortOrder(req.sortOrder());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
