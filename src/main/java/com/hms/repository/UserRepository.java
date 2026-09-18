package com.hms.repository;

import com.hms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * No explicit hospital filter in the query methods below — relies on the
 * tenantFilter Hibernate filter (enabled per-request by
 * TenantResolvingFilter) the same way Django relies on TenantManager to
 * scope every plain queryset. Never bypass this with a native query without
 * adding the hospital condition by hand.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByRoleAndActiveTrueOrderByUsernameAsc(User.Role role);

    long countByActiveTrueAndRoleNot(User.Role role);

    @Query("select u.role, count(u) from User u where u.active = true and u.role <> :excludedRole group by u.role")
    List<Object[]> countActiveGroupedByRoleExcluding(@Param("excludedRole") User.Role excludedRole);
}
