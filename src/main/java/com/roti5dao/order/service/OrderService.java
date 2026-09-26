package com.roti5dao.order.service;

import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.security.AuthUser;
import com.roti5dao.common.sequence.DailyCounterService;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.util.PhoneUtils;
import com.roti5dao.order.dto.OrderDtos.CartItemRequest;
import com.roti5dao.order.dto.OrderDtos.OnlineOrderRequest;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.dto.OrderDtos.QuoteResponse;
import com.roti5dao.order.dto.OrderDtos.WalkInOrderRequest;
import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.entity.OrderItem;
import com.roti5dao.order.entity.OrderItemOption;
import com.roti5dao.order.entity.OrderStatus;
import com.roti5dao.order.event.OrderEvents.OrderCancelledEvent;
import com.roti5dao.order.event.OrderEvents.OrderCompletedEvent;
import com.roti5dao.order.event.OrderEvents.OrderPlacedEvent;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingContext.CartLine;
import com.roti5dao.order.pricing.PricingContext.PricedLine;
import com.roti5dao.order.pricing.PricingService;
import com.roti5dao.order.repository.OrderRepository;
import com.roti5dao.payment.service.PaymentService;
import com.roti5dao.user.service.UserAccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final java.util.Set<OrderStatus> STAFF_TARGETS =
            java.util.EnumSet.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED);

    private final OrderRepository orderRepository;
    private final PricingService pricingService;
    private final OrderStatusMachine statusMachine;
    private final DailyCounterService counterService;
    private final SystemSettingService settings;
    private final UserAccountService userAccountService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;
    private final SecurityAuditService audit;
    private final Clock clock;
    private final AppProperties props;

    public OrderService(OrderRepository orderRepository, PricingService pricingService, OrderStatusMachine statusMachine,
                        DailyCounterService counterService, SystemSettingService settings,
                        UserAccountService userAccountService, PaymentService paymentService,
                        ApplicationEventPublisher events, SecurityAuditService audit, Clock clock, AppProperties props) {
        this.orderRepository = orderRepository;
        this.pricingService = pricingService;
        this.statusMachine = statusMachine;
        this.counterService = counterService;
        this.settings = settings;
        this.userAccountService = userAccountService;
        this.paymentService = paymentService;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
        this.props = props;
    }

    // ---------------------------------------------------------------- quote

    @Transactional(readOnly = true)
    public QuoteResponse quote(List<CartItemRequest> items, String promoCode, Integer redeemPoints, Long customerId,
                               OrderChannel channel) {
        return OrderMapper.quote(price(items, promoCode, redeemPoints, customerId, channel));
    }

    /** POS quote: ถ้าใส่เบอร์ต้องเป็นสมาชิก */
    @Transactional(readOnly = true)
    public QuoteResponse quoteWalkIn(List<CartItemRequest> items, String promoCode, Integer redeemPoints, String customerPhone) {
        return quote(items, promoCode, redeemPoints, resolveMember(customerPhone), OrderChannel.WALK_IN);
    }

    // ---------------------------------------------------------------- create

    /** ออเดอร์ออนไลน์ — login แล้วผูก customer อัตโนมัติ, guest ต้องส่งชื่อ + เบอร์ */
    @Transactional
    public OrderResponse createOnline(OnlineOrderRequest req, Optional<AuthUser> user) {
        if (!settings.getBoolean(SettingKey.SHOP_ACCEPT_ONLINE_ORDER)) {
            throw new BusinessException(ErrorCode.ONLINE_ORDER_CLOSED);
        }
        Long customerId = user.map(AuthUser::id).orElse(null);
        String guestName = null;
        String guestPhone = null;
        if (customerId == null) {
            if (req.guestName() == null || req.guestName().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "กรุณาระบุชื่อผู้สั่ง");
            }
            guestName = req.guestName().trim();
            guestPhone = UserAccountService.requirePhone(req.guestPhone());
        } else if (!userAccountService.isActiveMember(customerId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        PricingContext ctx = price(req.items(), req.promoCode(), req.redeemPoints(), customerId, OrderChannel.ONLINE);
        Order order = persist(ctx, OrderChannel.ONLINE, customerId, guestName, guestPhone, req.note(), null);
        return OrderMapper.full(order, List.of(), nickname(customerId));
    }

    /** POS เปิดบิล walk-in — ใส่เบอร์สมาชิกหรือไม่ก็ได้ */
    @Transactional
    public OrderResponse createWalkIn(WalkInOrderRequest req, Long staffId) {
        Long customerId = resolveMember(req.customerPhone());
        String guestName = customerId == null && req.guestName() != null && !req.guestName().isBlank()
                ? req.guestName().trim() : null;
        PricingContext ctx = price(req.items(), req.promoCode(), req.redeemPoints(), customerId, OrderChannel.WALK_IN);
        Order order = persist(ctx, OrderChannel.WALK_IN, customerId, guestName, null, req.note(), staffId);
        return OrderMapper.full(order, List.of(), nickname(customerId));
    }

    // ---------------------------------------------------------------- status

    /** พนักงานเลื่อนสถานะ CONFIRMED → PREPARING → READY → COMPLETED */
    @Transactional
    public OrderResponse changeStatus(Long orderId, OrderStatus target) {
        if (!STAFF_TARGETS.contains(target)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "ใช้ endpoint นี้เปลี่ยนเป็น PREPARING / READY / COMPLETED เท่านั้น");
        }
        Order order = lock(orderId);
        statusMachine.transition(order, target, Instant.now(clock));
        if (target == OrderStatus.COMPLETED) {
            events.publishEvent(new OrderCompletedEvent(order.getId(), order.getCustomerId(), order.getTotalAmount()));
        }
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(orderId), nickname(order.getCustomerId()));
    }

    /** ยกเลิก: คืนเงิน (mark REFUNDED) + คืนแต้ม/โควต้าโปร ผ่าน OrderCancelledEvent */
    @Transactional
    public OrderResponse cancel(Long orderId, String reason, Long staffId) {
        Order order = lock(orderId);
        statusMachine.transition(order, OrderStatus.CANCELLED, Instant.now(clock));
        order.setCancelReason(reason.trim());
        paymentService.voidForCancelledOrder(orderId, staffId);
        events.publishEvent(new OrderCancelledEvent(order.getId(), order.getCustomerId()));
        orderRepository.flush();
        audit.record(Event.ORDER_CANCELLED, order.getCustomerId(), "order=" + order.getOrderNo());
        return OrderMapper.full(order, paymentService.forOrder(orderId), nickname(order.getCustomerId()));
    }

    /** ยืนยันออเดอร์เมื่อยอดที่ชำระแล้ว ≥ ยอดสุทธิ (เรียกจาก OrderPaymentService) */
    void confirmIfFullyPaid(Order order) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && paymentService.paidTotal(order.getId()).compareTo(order.getTotalAmount()) >= 0) {
            statusMachine.transition(order, OrderStatus.CONFIRMED, Instant.now(clock));
        }
    }

    Order lock(Long orderId) {
        return orderRepository.findForUpdate(orderId).orElseThrow(() -> new NotFoundException("ออเดอร์"));
    }

    String nickname(Long customerId) {
        return customerId == null ? null : userAccountService.findNickname(customerId).orElse(null);
    }

    // ---------------------------------------------------------------- internals

    private PricingContext price(List<CartItemRequest> items, String promoCode, Integer redeemPoints, Long customerId,
                                 OrderChannel channel) {
        List<CartLine> cart = items.stream()
                .map(i -> new CartLine(i.productId(), i.quantity(), i.optionItemIds() == null ? List.of() : i.optionItemIds(), i.note()))
                .toList();
        PricingContext ctx = new PricingContext(channel, customerId, promoCode, redeemPoints == null ? 0 : redeemPoints,
                Instant.now(clock), cart);
        return pricingService.calculate(ctx);
    }

    private Long resolveMember(String customerPhone) {
        if (customerPhone == null || customerPhone.isBlank()) {
            return null;
        }
        if (!PhoneUtils.isValid(customerPhone)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "เบอร์โทรไม่ถูกต้อง");
        }
        return userAccountService.findActiveMemberIdByPhone(customerPhone)
                .orElseThrow(() -> new NotFoundException("สมาชิก"));
    }

    private Order persist(PricingContext ctx, OrderChannel channel, Long customerId, String guestName, String guestPhone,
                          String note, Long cashierId) {
        LocalDate today = LocalDate.now(clock.withZone(props.timezone()));
        int running = counterService.next(today, DailyCounterService.ORDER);
        int queue = counterService.next(today, DailyCounterService.QUEUE);

        Order order = new Order();
        order.setOrderNo("R5D-%s-%04d".formatted(today.format(ORDER_DATE), running));
        order.setTrackingToken(UUID.randomUUID());
        order.setChannel(channel);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCustomerId(customerId);
        order.setGuestName(guestName);
        order.setGuestPhone(guestPhone);
        order.setQueueNo(queue);
        order.setSubtotal(ctx.getSubtotal());
        order.setPromotionDiscount(ctx.getPromotionDiscount());
        order.setPointDiscount(ctx.getPointDiscount());
        order.setTotalAmount(ctx.total());
        order.setPointsRedeemed(ctx.getPointsRedeemed());
        order.setNote(note == null || note.isBlank() ? null : note.trim());
        order.setCashierId(cashierId);
        for (PricedLine l : ctx.getLines()) {
            OrderItem item = new OrderItem();
            item.setProductId(l.getProductId());
            item.setProductName(l.getProductName());
            item.setUnitPrice(l.getUnitPrice());
            item.setOptionsPrice(l.optionsPrice());
            item.setQuantity(l.getQuantity());
            item.setFreeQuantity(l.getFreeQuantity());
            item.setLineTotal(l.lineTotal());
            item.setNote(l.getNote());
            for (var o : l.getOptions()) {
                OrderItemOption opt = new OrderItemOption();
                opt.setOptionItemId(o.optionItemId());
                opt.setOptionGroupName(o.groupName());
                opt.setOptionName(o.name());
                opt.setExtraPrice(o.extraPrice());
                item.addOption(opt);
            }
            order.addItem(item);
        }
        orderRepository.saveAndFlush(order);

        // Phase 4 listener: บันทึกการใช้โปร (กันโควต้าเกิน) + ตัดแต้มที่แลก — ล้มเหลว = rollback ทั้งออเดอร์
        events.publishEvent(new OrderPlacedEvent(order.getId(), customerId, List.copyOf(ctx.getAppliedPromotions()),
                ctx.getPointsRedeemed()));

        // ยอด 0 บาท (ส่วนลดครบ) ไม่ต้องชำระ → ยืนยันทันที
        if (order.getTotalAmount().signum() == 0) {
            statusMachine.transition(order, OrderStatus.CONFIRMED, Instant.now(clock));
        }
        orderRepository.flush();
        return order;
    }
}
