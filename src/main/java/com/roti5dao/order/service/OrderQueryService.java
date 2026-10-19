package com.roti5dao.order.service;

import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.time.BusinessTime;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.order.dto.OrderDtos.BoardItem;
import com.roti5dao.order.dto.OrderDtos.OrderListItem;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.dto.OrderDtos.PendingPaymentItem;
import com.roti5dao.order.dto.OrderDtos.ReceiptResponse;
import com.roti5dao.order.dto.OrderDtos.TrackResponse;
import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.entity.OrderStatus;
import com.roti5dao.order.repository.OrderRepository;
import com.roti5dao.payment.dto.PaymentDtos.PaymentResponse;
import com.roti5dao.payment.entity.Payment;
import com.roti5dao.payment.entity.PaymentStatus;
import com.roti5dao.payment.service.PaymentService;
import com.roti5dao.user.service.UserAccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private static final Instant MIN = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("2999-01-01T00:00:00Z");
    /** สถานะที่แสดงบนคิวครัว: ทุกออเดอร์ที่ยังไม่ปิด/ยกเลิก (ร้านทำก่อน เก็บเงินตอนรับ) */
    private static final Set<OrderStatus> BOARD_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY));

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final UserAccountService userAccountService;
    private final SystemSettingService settings;
    private final BusinessTime time;

    public OrderQueryService(OrderRepository orderRepository, PaymentService paymentService,
                             UserAccountService userAccountService, SystemSettingService settings, BusinessTime time) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.userAccountService = userAccountService;
        this.settings = settings;
        this.time = time;
    }

    /** ติดตามด้วย tracking token (UUID) — ไม่ต้อง login */
    public TrackResponse track(UUID token) {
        Order o = orderRepository.findByTrackingToken(token).orElseThrow(() -> new NotFoundException("ออเดอร์"));
        return OrderMapper.track(o, paymentService.forOrder(o.getId()));
    }

    public PageResponse<OrderListItem> myOrders(Long customerId, Pageable pageable) {
        Page<Order> page = orderRepository.findByCustomerId(customerId, pageable);
        String nickname = userAccountService.findNickname(customerId).orElse(null);
        Map<Long, BigDecimal> paid = paidTotals(page.getContent());
        return PageResponse.of(page, o -> OrderMapper.listItem(o, nickname, paid.getOrDefault(o.getId(), BigDecimal.ZERO)));
    }

    /** เฉพาะออเดอร์ของตัวเอง — ของคนอื่นตอบ 404 (ไม่บอกว่ามีอยู่จริง) */
    public OrderResponse myOrder(Long customerId, String orderNo) {
        Order o = orderRepository.findByOrderNoAndCustomerId(orderNo, customerId)
                .orElseThrow(() -> new NotFoundException("ออเดอร์"));
        return full(o);
    }

    /** queue = true → คิวที่ยังไม่ปิด (ไม่สน status, เรียงตามเลขคิว) */
    public PageResponse<OrderListItem> search(OrderStatus status, OrderChannel channel, LocalDate date, boolean queue,
                                              Pageable pageable) {
        Instant from = date == null ? MIN : time.startOfDay(date);
        Instant to = date == null ? MAX : time.endOfDay(date);
        Page<Order> page = queue
                ? orderRepository.searchQueue(channel, from, to,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                : orderRepository.search(status, channel, from, to, pageable);
        Map<Long, String> names = nicknames(page.getContent());
        Map<Long, BigDecimal> paid = paidTotals(page.getContent());
        return PageResponse.of(page, o -> OrderMapper.listItem(o, names.get(o.getCustomerId()),
                paid.getOrDefault(o.getId(), BigDecimal.ZERO)));
    }

    /** ยอดที่ชำระแล้ว (PAID) ต่อออเดอร์ — query เดียวทั้งหน้า */
    private Map<Long, BigDecimal> paidTotals(List<Order> orders) {
        return paymentService.forOrders(orders.stream().map(Order::getId).toList()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .collect(Collectors.groupingBy(Payment::getOrderId,
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
    }

    public OrderResponse get(Long id) {
        return full(orderRepository.findWithItems(id).orElseThrow(() -> new NotFoundException("ออเดอร์")));
    }

    /** คิวครัว: ออเดอร์วันนี้ที่ยืนยันแล้วแต่ยังไม่เสร็จ */
    public List<BoardItem> board() {
        LocalDate today = time.today();
        List<Order> orders = orderRepository.findBoard(BOARD_STATUSES, time.startOfDay(today), time.endOfDay(today));
        Map<Long, String> names = nicknames(orders);
        return orders.stream().map(o -> OrderMapper.boardItem(o, names.get(o.getCustomerId()))).toList();
    }

    public ReceiptResponse receipt(Long id) {
        OrderResponse order = get(id);
        String cashier = order.cashierId() == null ? null : userAccountService.findNickname(order.cashierId()).orElse(null);
        return new ReceiptResponse(settings.getString(SettingKey.SHOP_NAME), settings.getString(SettingKey.SHOP_PHONE),
                order, cashier, time.now());
    }

    public PageResponse<PendingPaymentItem> payments(PaymentStatus status, Pageable pageable) {
        Page<Payment> page = paymentService.search(status, pageable);
        List<Long> orderIds = page.getContent().stream().map(Payment::getOrderId).distinct().toList();
        Map<Long, Order> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, String> names = nicknames(orders.values());
        return PageResponse.of(page, p -> {
            Order o = orders.get(p.getOrderId());
            return new PendingPaymentItem(PaymentResponse.of(p), o.getId(), o.getOrderNo(), o.getStatus(),
                    o.getTotalAmount(), OrderMapper.displayName(o, names.get(o.getCustomerId())));
        });
    }


    private OrderResponse full(Order o) {
        String nickname = o.getCustomerId() == null ? null : userAccountService.findNickname(o.getCustomerId()).orElse(null);
        return OrderMapper.full(o, paymentService.forOrder(o.getId()), nickname);
    }

    private Map<Long, String> nicknames(Collection<Order> orders) {
        return userAccountService.findNicknames(orders.stream().map(Order::getCustomerId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }
}
