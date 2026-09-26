package com.roti5dao.point.repository;

import com.roti5dao.point.entity.PointTransaction;
import com.roti5dao.point.entity.PointType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    /** ก้อนแต้มที่ยังใช้ได้ เรียงตามวันหมดอายุ (FIFO) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from PointTransaction t
            where t.customerId = :customerId and t.remaining > 0 and (t.expiresAt is null or t.expiresAt > :now)
            order by t.expiresAt asc nulls last, t.id asc
            """)
    List<PointTransaction> findUsableLotsForUpdate(Long customerId, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from PointTransaction t
            where t.customerId = :customerId and t.remaining > 0 and t.expiresAt <= :now
            order by t.expiresAt asc, t.id asc
            """)
    List<PointTransaction> findExpiredLotsForUpdate(Long customerId, Instant now);

    @Query("select distinct t.customerId from PointTransaction t where t.remaining > 0 and t.expiresAt <= :now")
    List<Long> findCustomersWithExpiredLots(Instant now, Pageable pageable);

    Optional<PointTransaction> findFirstByOrderIdAndType(Long orderId, PointType type);

    boolean existsByOrderIdAndType(Long orderId, PointType type);

    @Query("select coalesce(sum(t.points), 0) from PointTransaction t where t.orderId = :orderId and t.type = :type")
    int sumByOrderAndType(Long orderId, PointType type);

    Page<PointTransaction> findByCustomerId(Long customerId, Pageable pageable);

    @Query("""
            select coalesce(sum(t.remaining), 0) from PointTransaction t
            where t.customerId = :customerId and t.remaining > 0 and t.expiresAt > :now and t.expiresAt <= :until
            """)
    int sumExpiringBetween(Long customerId, Instant now, Instant until);

    @Query("""
            select min(t.expiresAt) from PointTransaction t
            where t.customerId = :customerId and t.remaining > 0 and t.expiresAt > :now
            """)
    Instant nextExpiry(Long customerId, Instant now);

    @Query("select coalesce(sum(t.points), 0) from PointTransaction t where t.customerId = :customerId")
    int sumLedger(Long customerId);
}
