package com.roti5dao.order.service;

import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.common.util.PhoneUtils;
import com.roti5dao.order.dto.OrderDtos.BoardItem;
import com.roti5dao.order.dto.OrderDtos.CustomerRef;
import com.roti5dao.order.dto.OrderDtos.Line;
import com.roti5dao.order.dto.OrderDtos.LineOption;
import com.roti5dao.order.dto.OrderDtos.OrderListItem;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.dto.OrderDtos.QuoteResponse;
import com.roti5dao.order.dto.OrderDtos.TrackResponse;
import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderItem;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.payment.dto.PaymentDtos.PaymentResponse;
import com.roti5dao.payment.dto.PaymentDtos.PublicPaymentResponse;
import com.roti5dao.payment.entity.Payment;
import com.roti5dao.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;

final class OrderMapper {

    private OrderMapper() {
    }

    static QuoteResponse quote(PricingContext ctx) {
        List<Line> lines = ctx.getLines().stream().map(l -> new Line(l.getProductId(), l.getProductName(),
                l.getUnitPrice(), l.optionsPrice(), l.getQuantity(), l.getFreeQuantity(), l.lineTotal(), l.getNote(),
                l.getOptions().stream().map(o -> new LineOption(o.groupName(), o.name(), o.extraPrice())).toList())).toList();
        return new QuoteResponse(lines, ctx.getSubtotal(), ctx.getPromotionDiscount(), ctx.getPointDiscount(),
                ctx.total(), ctx.getPointsRedeemed(), ctx.getPointsToEarn(), List.copyOf(ctx.getAppliedPromotions()));
    }

    static List<Line> lines(Order o) {
        return o.getItems().stream().map(OrderMapper::line).toList();
    }

    private static Line line(OrderItem i) {
        return new Line(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getOptionsPrice(), i.getQuantity(),
                i.getFreeQuantity(), i.getLineTotal(), i.getNote(),
                i.getOptions().stream().map(op -> new LineOption(op.getOptionGroupName(), op.getOptionName(), op.getExtraPrice())).toList());
    }

    static BigDecimal paid(List<Payment> payments) {
        return payments.stream().filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount).reduce(MoneyUtils.ZERO, BigDecimal::add);
    }

    static OrderResponse full(Order o, List<Payment> payments, String customerNickname) {
        BigDecimal paid = paid(payments);
        return new OrderResponse(o.getId(), o.getOrderNo(), o.getTrackingToken(), o.getChannel(), o.getStatus(),
                o.getQueueNo(), o.getCustomerId() == null ? null : new CustomerRef(o.getCustomerId(), customerNickname),
                o.getGuestName(), o.getGuestPhone(), lines(o), o.getSubtotal(), o.getPromotionDiscount(),
                o.getPointDiscount(), o.getTotalAmount(), paid, remaining(o, paid), o.getPointsRedeemed(),
                o.getPointsEarned(), o.getNote(), o.getCashierId(), payments.stream().map(PaymentResponse::of).toList(),
                o.getCreatedAt(), o.getConfirmedAt(), o.getCompletedAt(), o.getCancelledAt(), o.getCancelReason());
    }

    static TrackResponse track(Order o, List<Payment> payments) {
        BigDecimal paid = paid(payments);
        return new TrackResponse(o.getOrderNo(), o.getChannel(), o.getStatus(), o.getQueueNo(), o.getGuestName(),
                PhoneUtils.mask(o.getGuestPhone()), lines(o), o.getSubtotal(), o.getPromotionDiscount(),
                o.getPointDiscount(), o.getTotalAmount(), paid, remaining(o, paid),
                payments.stream().map(PublicPaymentResponse::of).toList(), o.getNote(), o.getCreatedAt(),
                o.getConfirmedAt(), o.getCompletedAt(), o.getCancelledAt());
    }

    static OrderListItem listItem(Order o, String customerNickname) {
        return new OrderListItem(o.getId(), o.getOrderNo(), o.getChannel(), o.getStatus(), o.getQueueNo(),
                displayName(o, customerNickname), o.getCustomerId() != null, o.getTotalAmount(), o.getCreatedAt());
    }

    static BoardItem boardItem(Order o, String customerNickname) {
        return new BoardItem(o.getId(), o.getOrderNo(), o.getChannel(), o.getStatus(), o.getQueueNo(),
                displayName(o, customerNickname), lines(o), o.getNote(), o.getCreatedAt(), o.getConfirmedAt());
    }

    static String displayName(Order o, String customerNickname) {
        if (o.getCustomerId() != null) {
            return customerNickname;
        }
        return o.getGuestName();
    }

    private static BigDecimal remaining(Order o, BigDecimal paid) {
        return MoneyUtils.max(MoneyUtils.ZERO, o.getTotalAmount().subtract(paid));
    }
}
