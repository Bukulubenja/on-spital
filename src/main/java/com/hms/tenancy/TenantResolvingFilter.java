package com.hms.tenancy;

import com.hms.entity.Hospital;
import com.hms.repository.HospitalRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Resolves the current request's Hospital from its subdomain and makes it
 * available via {@link TenantContext} plus the {@code tenantFilter}
 * Hibernate filter for the life of the request — direct analogue of
 * Django's TenantMiddleware (hospital/middleware.py).
 *
 * <p>Must run before Spring Security's authentication filters, since the
 * tenant-scoped UserDetailsService needs the context already set — same
 * ordering requirement Django has for AuthenticationMiddleware. Wired via
 * {@code httpSecurity.addFilterBefore(...)} in SecurityConfig, not
 * {@code @Component} auto-registration into the plain servlet chain, so its
 * position relative to Security is explicit rather than accidental.
 */
@Component
public class TenantResolvingFilter extends OncePerRequestFilter {

    private final HospitalRepository hospitalRepository;
    private final EntityManager entityManager;
    private final String baseDomainHost;
    private final boolean debugHeaderFallbackEnabled;

    public TenantResolvingFilter(
            HospitalRepository hospitalRepository,
            EntityManager entityManager,
            @Value("${app.base-domain}") String baseDomain,
            @Value("${app.debug}") boolean debugHeaderFallbackEnabled
    ) {
        this.hospitalRepository = hospitalRepository;
        this.entityManager = entityManager;
        // app.base-domain may include a port (e.g. "lvh.me:8000"), matching
        // Django's BASE_DOMAIN — only the host part is relevant here.
        this.baseDomainHost = baseDomain.split(":")[0];
        this.debugHeaderFallbackEnabled = debugHeaderFallbackEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getServerName();

        String subdomain = null;
        if (host.endsWith("." + baseDomainHost)) {
            subdomain = host.substring(0, host.length() - baseDomainHost.length() - 1);
        } else if (debugHeaderFallbackEnabled) {
            // DEBUG-only escape hatch for mobile dev, mirroring Django's
            // settings.DEBUG-gated X-Hospital-Subdomain fallback. This gate
            // must never be removed/loosened — an unconditional version
            // would let any client pick an arbitrary tenant via a header.
            String header = request.getHeader("X-Hospital-Subdomain");
            subdomain = (header == null || header.isBlank()) ? null : header;
        }

        Long hospitalId = null;
        if (subdomain != null) {
            Optional<Hospital> hospital = hospitalRepository.findBySubdomainAndActiveTrue(subdomain);
            if (hospital.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No such hospital");
                return;
            }
            hospitalId = hospital.get().getId();
        }

        TenantContext.set(hospitalId);
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("hospitalId", hospitalId);
        try {
            chain.doFilter(request, response);
        } finally {
            session.disableFilter("tenantFilter");
            TenantContext.clear();
        }
    }
}
