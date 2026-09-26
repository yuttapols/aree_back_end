package com.roti5dao.order.repository;

import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.trackingToken = :token")
    Optional<Order> findByTrackingTokenForUpdate(UUID token);

    @Query("select distinct o from Order o left join fetch o.items where o.trackingToken = :token")
    Optional<Order> findByTrackingToken(UUID token);

    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findWithItems(Long id);

    @Query("select distinct o from Order o left join fetch o.items where o.orderNo = :orderNo and o.customerId = :customerId")
    Optional<Order> findByOrderNoAndCustomerId(String orderNo, Long customerId);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    @Query(value = """
            select o from Order o
            where (:status is null or o.status = :status)
              and (:channel is null or o.channel = :channel)
              and o.createdAt >= :from and o.createdAt < :to
            """,
            countQuery = """
            select count(o) from Order o
            where (:status is null or o.status = :status)
              and (:channel is null or o.channel = :channel)
              and o.createdAt >= :from and o.createdAt < :to
            """)
    Page<Order> search(OrderStatus status, OrderChannel channel, Instant from, Instant to, Pageable pageable);

    @Query("""
            select distinct o from Order o left join fetch o.items
            where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
            order by o.queueNo
            """)
    List<Order> findBoard(Collection<OrderStatus> statuses, Instant from, Instant to);
}
