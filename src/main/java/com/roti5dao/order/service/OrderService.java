package com.roti5dao.order.service;

import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.security.AuthUser;
import com.roti5dao.common.sequence.DailyCounterService;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.time.BusinessTime;
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
import com.roti5dao.order.event.OrderEvents.OrderItemsChangingEvent;
import com.roti5dao.order.event.OrderEvents.OrderPlacedEvent;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingContext.CartLine;
import com.roti5dao.order.pricing.PricingContext.PricedLine;
import com.roti5dao.order.pricing.PricingService;
import com.roti5dao.order.repository.OrderRepository;
import com.roti5dao.payment.service.PaymentService;
import com.roti5dao.user.service.UserAccountService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final BusinessTime time;

    public OrderService(OrderRepository orderRepository, PricingService pricingService, OrderStatusMachine statusMachine,
                        DailyCounterService counterService, SystemSettingService settings,
                        UserAccountService userAccountService, PaymentService paymentService,
                        ApplicationEventPublisher events, SecurityAuditService audit, BusinessTime time) {
        this.orderRepository = orderRepository;
        this.pricingService = pricingService;
        this.statusMachine = statusMachine;
        this.counterService = counterService;
        this.settings = settings;
        this.userAccountService = userAccountService;
        this.paymentService = paymentService;
        this.events = events;
        this.audit = audit;
        this.time = time;
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
        if (Boolean.TRUE.equals(req.payLater())) {
            // "ยังไม่ชำระ" — เข้าคิวครัวเลย เก็บเงินทีหลังผ่าน POST /admin/orders/{id}/payments
            order.setPayLater(true);
            statusMachine.transition(order, OrderStatus.CONFIRMED, time.now());
            orderRepository.flush();
        }
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
        if (target == OrderStatus.COMPLETED && order.isPayLater()) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "ออเดอร์นี้ยังชำระไม่ครบ — รับเงินก่อนปิดออเดอร์");
        }
        statusMachine.transition(order, target, time.now());
        if (target == OrderStatus.COMPLETED) {
            events.publishEvent(new OrderCompletedEvent(order.getId(), order.getCustomerId(), order.getTotalAmount()));
        }
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(orderId), nickname(order.getCustomerId()));
    }

    /** "ยังไม่ชำระ" สำหรับออเดอร์ที่เปิดไว้แล้ว (หน้าร้านหรือออนไลน์มารับ) — payment PENDING ที่ค้างอยู่คงไว้ */
    @Transactional
    public OrderResponse markPayLater(Long orderId) {
        Order order = lock(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "เปลี่ยนเป็นยังไม่ชำระได้เฉพาะออเดอร์ที่รอชำระ");
        }
        order.setPayLater(true);
        statusMachine.transition(order, OrderStatus.CONFIRMED, time.now());
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(orderId), nickname(order.getCustomerId()));
    }

    /** ปิดออเดอร์หน้าเคาน์เตอร์ในครั้งเดียว — เดินทุกขั้นที่เหลือใน transaction เดียว (พังกลางทาง = rollback ทั้งหมด) */
    @Transactional
    public OrderResponse complete(Long orderId) {
        Order order = lock(orderId);
        if (order.isPayLater()) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "ออเดอร์นี้ยังชำระไม่ครบ — รับเงินก่อนปิดออเดอร์");
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "ปิดออเดอร์ได้เมื่อชำระแล้วและยังไม่เสร็จ/ยกเลิก");
        }
        var now = time.now();
        for (OrderStatus next : List.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED)) {
            if (statusMachine.canTransition(order.getStatus(), next)) {
                statusMachine.transition(order, next, now);
            }
        }
        events.publishEvent(new OrderCompletedEvent(order.getId(), order.getCustomerId(), order.getTotalAmount()));
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(orderId), nickname(order.getCustomerId()));
    }

    /** ยกเลิก: คืนเงิน (mark REFUNDED) + คืนแต้ม/โควต้าโปร ผ่าน OrderCancelledEvent */
    @Transactional
    public OrderResponse cancel(Long orderId, String reason, Long staffId) {
        Order order = lock(orderId);
        statusMachine.transition(order, OrderStatus.CANCELLED, time.now());
        order.setCancelReason(reason.trim());
        order.setPayLater(false);
        paymentService.voidForCancelledOrder(orderId, staffId);
        events.publishEvent(new OrderCancelledEvent(order.getId(), order.getCustomerId()));
        orderRepository.flush();
        audit.record(Event.ORDER_CANCELLED, order.getCustomerId(), "order=" + order.getOrderNo());
        return OrderMapper.full(order, paymentService.forOrder(orderId), nickname(order.getCustomerId()));
    }

    /** ยืนยันออเดอร์เมื่อยอดที่ชำระแล้ว ≥ ยอดสุทธิ (เรียกจาก OrderPaymentService) */
    void confirmIfFullyPaid(Order order) {
        if (paymentService.paidTotal(order.getId()).compareTo(order.getTotalAmount()) < 0) {
            return;
        }
        order.setPayLater(false);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            statusMachine.transition(order, OrderStatus.CONFIRMED, time.now());
        }
    }

    Order lock(Long orderId) {
        return orderRepository.findForUpdate(orderId).orElseThrow(() -> new NotFoundException("ออเดอร์"));
    }

    String nickname(Long customerId) {
        return customerId == null ? null : userAccountService.findNickname(customerId).orElse(null);
    }

    // ---------------------------------------------------------------- แก้รายการ (POS)

    private static final java.util.Set<OrderStatus> EDITABLE =
            java.util.EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);

    /** เพิ่มรายการ */
    @Transactional
    public OrderResponse addItem(Long orderId, CartItemRequest req) {
        Order order = lockEditable(orderId);
        List<CartItemRequest> cart = new ArrayList<>(currentCart(order, null));
        cart.add(req);
        return reprice(order, cart);
    }

    /** แก้รายการ (เปลี่ยนจำนวน / ตัวเลือก / หมายเหตุ / เมนู) */
    @Transactional
    public OrderResponse updateItem(Long orderId, Long itemId, CartItemRequest req) {
        Order order = lockEditable(orderId);
        requireItem(order, itemId);
        List<CartItemRequest> cart = new ArrayList<>();
        for (OrderItem i : order.getItems()) {
            cart.add(i.getId().equals(itemId) ? req : toCart(i));
        }
        return reprice(order, cart);
    }

    /** ลบรายการ — ลบรายการสุดท้ายไม่ได้ (ให้ยกเลิกออเดอร์แทน) */
    @Transactional
    public OrderResponse removeItem(Long orderId, Long itemId) {
        Order order = lockEditable(orderId);
        requireItem(order, itemId);
        List<CartItemRequest> cart = currentCart(order, itemId);
        if (cart.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "ลบรายการสุดท้ายไม่ได้ — ให้ยกเลิกออเดอร์แทน");
        }
        return reprice(order, cart);
    }

    private Order lockEditable(Long orderId) {
        Order order = lock(orderId);
        if (order.getChannel() != OrderChannel.WALK_IN) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "แก้รายการได้เฉพาะออเดอร์หน้าร้าน");
        }
        if (!EDITABLE.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "แก้รายการได้ก่อนครัวเริ่มทำเท่านั้น");
        }
        if (order.getPointsRedeemed() > 0) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "ออเดอร์นี้ใช้แต้มแลกส่วนลด — ให้ยกเลิกแล้วเปิดบิลใหม่");
        }
        return order;
    }

    private static void requireItem(Order order, Long itemId) {
        if (order.getItems().stream().noneMatch(i -> i.getId().equals(itemId))) {
            throw new NotFoundException("รายการในออเดอร์");
        }
    }

    private static List<CartItemRequest> currentCart(Order order, Long excludeItemId) {
        return order.getItems().stream().filter(i -> !i.getId().equals(excludeItemId)).map(OrderService::toCart).toList();
    }

    /** ตัวเลือกที่ถูกลบไปแล้ว (option_item_id = null) จะหลุดออกตอนคำนวณใหม่ */
    private static CartItemRequest toCart(OrderItem i) {
        return new CartItemRequest(i.getProductId(), i.getQuantity(),
                i.getOptions().stream().map(OrderItemOption::getOptionItemId).filter(java.util.Objects::nonNull).toList(),
                i.getNote());
    }

    /**
     * คำนวณใหม่ทั้งบิล (ราคาปัจจุบัน + โปร) แล้วแทนที่รายการเดิม — เลขออเดอร์/คิวคงเดิม
     * ยอดใหม่ต่ำกว่าที่จ่ายไปแล้ว → ไม่ให้แก้ (ระบบยังไม่มีคืนเงินบางส่วน)
     */
    private OrderResponse reprice(Order order, List<CartItemRequest> cart) {
        if (cart.size() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "รายการในออเดอร์ได้สูงสุด 50 รายการ");
        }
        events.publishEvent(new OrderItemsChangingEvent(order.getId())); // คืนโควต้าโปรเดิมก่อนคำนวณ
        PricingContext ctx = price(cart, order.getPromoCode(), 0, order.getCustomerId(), order.getChannel());
        BigDecimal paid = paymentService.paidTotal(order.getId());
        if (ctx.total().compareTo(paid) < 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "ยอดใหม่ " + ctx.total() + " บาท ต่ำกว่าที่ชำระแล้ว " + paid + " บาท — ให้ยกเลิกออเดอร์แล้วเปิดใหม่");
        }
        order.setSubtotal(ctx.getSubtotal());
        order.setPromotionDiscount(ctx.getPromotionDiscount());
        order.setPointDiscount(ctx.getPointDiscount());
        order.setTotalAmount(ctx.total());
        order.getItems().clear();
        orderRepository.flush();
        fillItems(order, ctx);
        orderRepository.flush();
        events.publishEvent(new OrderPlacedEvent(order.getId(), order.getCustomerId(),
                List.copyOf(ctx.getAppliedPromotions()), 0));

        if (paid.compareTo(order.getTotalAmount()) >= 0) {
            confirmIfFullyPaid(order);
        } else if (order.getStatus() == OrderStatus.CONFIRMED) {
            order.setPayLater(true); // อยู่ในคิวแล้วแต่ยอดเพิ่ม → ค้างเก็บส่วนต่าง
        }
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(order.getId()), nickname(order.getCustomerId()));
    }

    // ---------------------------------------------------------------- internals

    private PricingContext price(List<CartItemRequest> items, String promoCode, Integer redeemPoints, Long customerId,
                                 OrderChannel channel) {
        List<CartLine> cart = items.stream()
                .map(i -> new CartLine(i.productId(), i.quantity(), i.optionItemIds() == null ? List.of() : i.optionItemIds(), i.note()))
                .toList();
        PricingContext ctx = new PricingContext(channel, customerId, promoCode, redeemPoints == null ? 0 : redeemPoints,
                time.now(), cart);
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
        LocalDate today = time.today();
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
        order.setPromoCode(ctx.getPromoCode() == null || ctx.getPromoCode().isBlank() ? null : ctx.getPromoCode().trim());
        fillItems(order, ctx);
        orderRepository.saveAndFlush(order);

        // Phase 4 listener: บันทึกการใช้โปร (กันโควต้าเกิน) + ตัดแต้มที่แลก — ล้มเหลว = rollback ทั้งออเดอร์
        events.publishEvent(new OrderPlacedEvent(order.getId(), customerId, List.copyOf(ctx.getAppliedPromotions()),
                ctx.getPointsRedeemed()));

        // ยอด 0 บาท (ส่วนลดครบ) ไม่ต้องชำระ → ยืนยันทันที
        if (order.getTotalAmount().signum() == 0) {
            statusMachine.transition(order, OrderStatus.CONFIRMED, time.now());
        }
        orderRepository.flush();
        return order;
    }

    private static void fillItems(Order order, PricingContext ctx) {
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
    }
}
