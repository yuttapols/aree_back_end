package com.roti5dao.user.repository;

import com.roti5dao.user.entity.AppUser;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.entity.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByPhone(String phone);

    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    long countByRoleAndStatus(Role role, UserStatus status);

    List<AppUser> findByRoleInOrderByIdAsc(Collection<Role> roles);
}
