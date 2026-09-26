package com.roti5dao.payment.repository;

import com.roti5dao.payment.entity.Payment;
import com.roti5dao.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.method where p.orderId = :orderId order by p.id")
    List<Payment> findByOrderId(Long orderId);

    @Query("select p from Payment p join fetch p.method where p.orderId in :orderIds order by p.id")
    List<Payment> findByOrderIdIn(Collection<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.method where p.id = :id")
    Optional<Payment> findForUpdate(Long id);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.orderId = :orderId and p.status = :status")
    BigDecimal sumByOrderAndStatus(Long orderId, PaymentStatus status);

    long countByOrderIdAndStatus(Long orderId, PaymentStatus status);

    @Query(value = "select p from Payment p join fetch p.method where (:status is null or p.status = :status)",
            countQuery = "select count(p) from Payment p where (:status is null or p.status = :status)")
    Page<Payment> search(PaymentStatus status, Pageable pageable);
}
