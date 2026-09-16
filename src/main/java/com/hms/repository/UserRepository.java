package com.hms.repository;

import com.hms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
