package com.roti5dao.payment.dto;

import com.roti5dao.payment.entity.Payment;
import com.roti5dao.payment.entity.PaymentMethod;
import com.roti5dao.payment.entity.PaymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentMethodResponse(Long id, String code, String name, boolean requiresSlip,
                                        boolean requiresReference, boolean allowOnline, String instruction, String icon,
                                        boolean active, int sortOrder) {
        public static PaymentMethodResponse of(PaymentMethod m) {
            return new PaymentMethodResponse(m.getId(), m.getCode(), m.getName(), m.isRequiresSlip(),
                    m.isRequiresReference(), m.isAllowOnline(), m.getInstruction(), m.getIcon(), m.isActive(),
                    m.getSortOrder());
        }
    }

    /** ข้อมูลที่ลูกค้าเห็น — PromptPay ID / บัญชีโอน มาจาก system_setting */
    public record PublicPaymentMethod(String code, String name, boolean requiresSlip, boolean requiresReference,
                                      String instruction, String icon, String promptPayId, String bankAccount) {
    }

    public record PaymentMethodUpsertRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "ใช้ได้เฉพาะ A-Z 0-9 _") String code,
            @NotBlank @Size(max = 100) String name,
            boolean requiresSlip,
            boolean requiresReference,
            boolean allowOnline,
            @Size(max = 1000) String instruction,
            @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9 _-]*$", message = "icon ไม่ถูกต้อง") String icon,
            boolean active,
            @Min(0) @Max(100000) int sortOrder) {
    }

    public record PaymentResponse(Long id, Long orderId, String methodCode, String methodName, BigDecimal amount,
                                  BigDecimal cashReceived, BigDecimal changeAmount, String referenceNo, boolean hasSlip,
                                  PaymentStatus status, Instant paidAt, Long verifiedBy, String rejectReason,
                                  Instant createdAt) {
        public static PaymentResponse of(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getMethod().getCode(), p.getMethod().getName(),
                    p.getAmount(), p.getCashReceived(), p.getChangeAmount(), p.getReferenceNo(), p.getSlipKey() != null,
                    p.getStatus(), p.getPaidAt(), p.getVerifiedBy(), p.getRejectReason(), p.getCreatedAt());
        }
    }

    /** มุมมองของลูกค้า (หน้า track) — ไม่เปิดเผยข้อมูลพนักงานผู้ตรวจ */
    public record PublicPaymentResponse(Long id, String methodCode, String methodName, BigDecimal amount,
                                        PaymentStatus status, String rejectReason, Instant createdAt) {
        public static PublicPaymentResponse of(Payment p) {
            return new PublicPaymentResponse(p.getId(), p.getMethod().getCode(), p.getMethod().getName(), p.getAmount(),
                    p.getStatus(), p.getRejectReason(), p.getCreatedAt());
        }
    }
}
