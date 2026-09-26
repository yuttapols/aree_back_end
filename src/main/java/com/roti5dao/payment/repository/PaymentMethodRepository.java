package com.roti5dao.payment.repository;

import com.roti5dao.payment.entity.PaymentMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findAllByOrderBySortOrderAscIdAsc();

    List<PaymentMethod> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<PaymentMethod> findByCode(String code);

    boolean existsByCode(String code);
}
