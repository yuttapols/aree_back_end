package com.roti5dao.user.repository;

import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.entity.Role;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerProfile p where p.userId = :userId")
    Optional<CustomerProfile> findForUpdate(Long userId);

    @Query("select p from CustomerProfile p join fetch p.user u where u.phone = :phone")
    Optional<CustomerProfile> findByPhone(String phone);

    @Query(value = """
            select p from CustomerProfile p join fetch p.user u
            where u.role = :role
              and (:pattern is null
                   or u.phone like :pattern escape '\\'
                   or lower(coalesce(u.email, '')) like :pattern escape '\\'
                   or lower(p.memberCode) like :pattern escape '\\'
                   or lower(coalesce(p.nickname, '')) like :pattern escape '\\'
                   or lower(coalesce(p.firstName, '')) like :pattern escape '\\'
                   or lower(coalesce(p.lastName, '')) like :pattern escape '\\')
            """,
            countQuery = """
            select count(p) from CustomerProfile p join p.user u
            where u.role = :role
              and (:pattern is null
                   or u.phone like :pattern escape '\\'
                   or lower(coalesce(u.email, '')) like :pattern escape '\\'
                   or lower(p.memberCode) like :pattern escape '\\'
                   or lower(coalesce(p.nickname, '')) like :pattern escape '\\'
                   or lower(coalesce(p.firstName, '')) like :pattern escape '\\'
                   or lower(coalesce(p.lastName, '')) like :pattern escape '\\')
            """)
    Page<CustomerProfile> search(Role role, String pattern, Pageable pageable);
}
