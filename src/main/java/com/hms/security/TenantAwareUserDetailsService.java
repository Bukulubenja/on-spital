package com.hms.security;

import com.hms.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up a User by username, implicitly scoped to the current request's
 * hospital via the tenantFilter Hibernate filter (enabled earlier in the
 * chain by TenantResolvingFilter) — the Java analogue of ModelBackend's
 * lookup going through the tenant-scoped TenantUserManager in Django.
 */
@Service
public class TenantAwareUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public TenantAwareUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(HmsUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
