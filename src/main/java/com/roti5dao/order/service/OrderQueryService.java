package com.roti5dao.order.service;

import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.storage.FileStorageService.LoadedFile;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private static final Instant MIN = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("2999-01-01T00:00:00Z");

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final UserAccountService userAccountService;
    private final SystemSettingService settings;
    private final Clock clock;
    private final ZoneId zone;

    public OrderQueryService(OrderRepository orderRepository, PaymentService paymentService,
                             UserAccountService userAccountService, SystemSettingService settings, Clock clock,
                             AppProperties props) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.userAccountService = userAccountService;
        this.settings = settings;
        this.clock = clock;
        this.zone = props.timezone();
    }

    /** ติดตามด้วย tracking token (UUID) — ไม่ต้อง login */
    public TrackResponse track(UUID token) {
        Order o = orderRepository.findByTrackingToken(token).orElseThrow(() -> new NotFoundException("ออเดอร์"));
        return OrderMapper.track(o, paymentService.forOrder(o.getId()));
    }

    public PageResponse<OrderListItem> myOrders(Long customerId, Pageable pageable) {
        Page<Order> page = orderRepository.findByCustomerId(customerId, pageable);
        String nickname = userAccountService.findNickname(customerId).orElse(null);
        return PageResponse.of(page, o -> OrderMapper.listItem(o, nickname));
    }

    /** เฉพาะออเดอร์ของตัวเอง — ของคนอื่นตอบ 404 (ไม่บอกว่ามีอยู่จริง) */
    public OrderResponse myOrder(Long customerId, String orderNo) {
        Order o = orderRepository.findByOrderNoAndCustomerId(orderNo, customerId)
                .orElseThrow(() -> new NotFoundException("ออเดอร์"));
        return full(o);
    }

    public PageResponse<OrderListItem> search(OrderStatus status, OrderChannel channel, LocalDate date, Pageable pageable) {
        Instant from = date == null ? MIN : date.atStartOfDay(zone).toInstant();
        Instant to = date == null ? MAX : date.plusDays(1).atStartOfDay(zone).toInstant();
        Page<Order> page = orderRepository.search(status, channel, from, to, pageable);
        Map<Long, String> names = nicknames(page.getContent());
        return PageResponse.of(page, o -> OrderMapper.listItem(o, names.get(o.getCustomerId())));
    }

    public OrderResponse get(Long id) {
        return full(orderRepository.findWithItems(id).orElseThrow(() -> new NotFoundException("ออเดอร์")));
    }

    /** คิวครัว: ออเดอร์วันนี้ที่ยืนยันแล้วแต่ยังไม่เสร็จ */
    public List<BoardItem> board() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Order> orders = orderRepository.findBoard(
                EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY),
                today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
        Map<Long, String> names = nicknames(orders);
        return orders.stream().map(o -> OrderMapper.boardItem(o, names.get(o.getCustomerId()))).toList();
    }

    public ReceiptResponse receipt(Long id) {
        OrderResponse order = get(id);
        String cashier = order.cashierId() == null ? null : userAccountService.findNickname(order.cashierId()).orElse(null);
        return new ReceiptResponse(settings.getString(SettingKey.SHOP_NAME), settings.getString(SettingKey.SHOP_PHONE),
                order, cashier, Instant.now(clock));
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

    public LoadedFile slip(Long paymentId) {
        return paymentService.loadSlip(paymentService.get(paymentId));
    }

    private OrderResponse full(Order o) {
        String nickname = o.getCustomerId() == null ? null : userAccountService.findNickname(o.getCustomerId()).orElse(null);
        return OrderMapper.full(o, paymentService.forOrder(o.getId()), nickname);
    }

    private Map<Long, String> nicknames(java.util.Collection<Order> orders) {
        return userAccountService.findNicknames(orders.stream().map(Order::getCustomerId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }
}
