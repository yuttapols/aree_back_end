package com.roti5dao.order.dto;

import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.entity.OrderStatus;
import com.roti5dao.order.pricing.PricingContext.AppliedPromotion;
import com.roti5dao.payment.dto.PaymentDtos.PaymentResponse;
import com.roti5dao.payment.dto.PaymentDtos.PublicPaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ---------- Requests ----------

    public record CartItemRequest(@NotNull Long productId,
                                  @Min(1) @Max(99) int quantity,
                                  @Size(max = 20) List<@NotNull Long> optionItemIds,
                                  @Size(max = 300) String note) {
    }

    public record QuoteRequest(@NotEmpty @Size(max = 50) List<@Valid CartItemRequest> items,
                               @Size(max = 30) String promoCode,
                               @Min(0) @Max(10_000_000) Integer redeemPoints) {
    }

    public record OnlineOrderRequest(@NotEmpty @Size(max = 50) List<@Valid CartItemRequest> items,
                                     @Size(max = 30) String promoCode,
                                     @Min(0) @Max(10_000_000) Integer redeemPoints,
                                     @Size(max = 100) String guestName,
                                     @Size(max = 20) String guestPhone,
                                     @Size(max = 500) String note) {
    }

    public record AdminQuoteRequest(@NotEmpty @Size(max = 50) List<@Valid CartItemRequest> items,
                                    @Size(max = 30) String promoCode,
                                    @Min(0) @Max(10_000_000) Integer redeemPoints,
                                    @Size(max = 20) String customerPhone) {
    }

    public record WalkInOrderRequest(@NotEmpty @Size(max = 50) List<@Valid CartItemRequest> items,
                                     @Size(max = 30) String promoCode,
                                     @Min(0) @Max(10_000_000) Integer redeemPoints,
                                     @Size(max = 20) String customerPhone,
                                     @Size(max = 100) String guestName,
                                     @Size(max = 500) String note) {
    }

    /** amount ว่าง = ชำระยอดคงค้างทั้งหมด */
    public record StaffPaymentRequest(@NotBlank @Size(max = 30) String methodCode,
                                      @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                                      @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal cashReceived,
                                      @Size(max = 100) String referenceNo) {
    }

    public record VerifyPaymentRequest(@NotNull Boolean approve, @Size(max = 300) String rejectReason) {
    }

    public record StatusChangeRequest(@NotNull OrderStatus status) {
    }

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }

    // ---------- Responses ----------

    public record LineOption(String groupName, String name, BigDecimal extraPrice) {
    }

    public record Line(Long productId, String productName, BigDecimal unitPrice, BigDecimal optionsPrice, int quantity,
                       int freeQuantity, BigDecimal lineTotal, String note, List<LineOption> options) {
    }

    public record QuoteResponse(List<Line> items, BigDecimal subtotal, BigDecimal promotionDiscount,
                                BigDecimal pointDiscount, BigDecimal total, int pointsRedeemed, int pointsToEarn,
                                List<AppliedPromotion> appliedPromotions) {
    }

    public record CustomerRef(Long id, String nickname) {
    }

    /** รายละเอียดเต็ม — สำหรับเจ้าของออเดอร์และพนักงาน */
    public record OrderResponse(Long id, String orderNo, UUID trackingToken, OrderChannel channel, OrderStatus status,
                                int queueNo, CustomerRef customer, String guestName, String guestPhone,
                                List<Line> items, BigDecimal subtotal, BigDecimal promotionDiscount,
                                BigDecimal pointDiscount, BigDecimal totalAmount, BigDecimal paidAmount,
                                BigDecimal remainingAmount, int pointsRedeemed, int pointsEarned, String note,
                                Long cashierId, List<PaymentResponse> payments, Instant createdAt,
                                Instant confirmedAt, Instant completedAt, Instant cancelledAt, String cancelReason) {
    }

    /** มุมมองสาธารณะผ่าน tracking link — ปิดบังเบอร์โทร ไม่แสดงข้อมูลภายใน */
    public record TrackResponse(String orderNo, OrderChannel channel, OrderStatus status, int queueNo, String guestName,
                                String maskedPhone, List<Line> items, BigDecimal subtotal, BigDecimal promotionDiscount,
                                BigDecimal pointDiscount, BigDecimal totalAmount, BigDecimal paidAmount,
                                BigDecimal remainingAmount, List<PublicPaymentResponse> payments, String note,
                                Instant createdAt, Instant confirmedAt, Instant completedAt, Instant cancelledAt) {
    }

    public record OrderListItem(Long id, String orderNo, OrderChannel channel, OrderStatus status, int queueNo,
                                String customerName, boolean member, BigDecimal totalAmount, Instant createdAt) {
    }

    public record BoardItem(Long id, String orderNo, OrderChannel channel, OrderStatus status, int queueNo,
                            String customerName, List<Line> items, String note, Instant createdAt, Instant confirmedAt) {
    }

    public record ReceiptResponse(String shopName, String shopPhone, OrderResponse order, String cashierName,
                                  Instant printedAt) {
    }

    public record PendingPaymentItem(PaymentResponse payment, Long orderId, String orderNo, OrderStatus orderStatus,
                                     BigDecimal orderTotal, String customerName) {
    }
}
