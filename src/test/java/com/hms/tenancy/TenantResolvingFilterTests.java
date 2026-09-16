package com.hms.tenancy;

import com.hms.entity.Hospital;
import com.hms.repository.HospitalRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of TenantResolvingFilter's host-parsing and DEBUG
 * gating logic, with HospitalRepository/Session mocked out — a lighter
 * substitute for hitting a real Postgres DB in Phase 1. Worth upgrading to
 * a full @SpringBootTest + real database pass (mirroring Django's
 * TenantIsolationTests) once there's a second tenant-scoped entity to
 * actually prove cross-tenant isolation with.
 */
class TenantResolvingFilterTests {

    private final HospitalRepository hospitalRepository = mock(HospitalRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Session session = mock(Session.class);
    private final Filter hibernateFilter = mock(Filter.class);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private TenantResolvingFilter filterWith(String baseDomain, boolean debugEnabled) {
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter("tenantFilter")).thenReturn(hibernateFilter);
        return new TenantResolvingFilter(hospitalRepository, entityManager, baseDomain, debugEnabled);
    }

    @Test
    void resolvesHospitalFromSubdomainAndClearsContextAfterTheRequest() throws Exception {
        when(hospitalRepository.findBySubdomainAndActiveTrue("stjohns"))
                .thenReturn(Optional.of(hospitalWithId(1L)));

        TenantResolvingFilter filter = filterWith("lvh.me:8000", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("stjohns.lvh.me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(hibernateFilter).setParameter("hospitalId", 1L);
        verify(session).disableFilter("tenantFilter");
        assertThat(chain.getRequest()).isNotNull(); // chain proceeded
        assertThat(TenantContext.get()).isNull(); // cleared once the request finished
    }

    @Test
    void returns404WhenSubdomainMatchesNoActiveHospital() throws Exception {
        when(hospitalRepository.findBySubdomainAndActiveTrue("ghost")).thenReturn(Optional.empty());

        TenantResolvingFilter filter = filterWith("lvh.me:8000", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("ghost.lvh.me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull(); // never reached the rest of the chain
    }

    @Test
    void debugHeaderFallbackIsInertWhenDebugDisabled() throws Exception {
        TenantResolvingFilter filter = filterWith("lvh.me:8000", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("192.168.1.5"); // not a subdomain of the base domain
        request.addHeader("X-Hospital-Subdomain", "stjohns");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(hospitalRepository); // header never even looked at a hospital
        verify(hibernateFilter).setParameter("hospitalId", null);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void debugHeaderFallbackResolvesTenantWhenDebugEnabled() throws Exception {
        when(hospitalRepository.findBySubdomainAndActiveTrue("stjohns"))
                .thenReturn(Optional.of(hospitalWithId(2L)));

        TenantResolvingFilter filter = filterWith("lvh.me:8000", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("192.168.1.5");
        request.addHeader("X-Hospital-Subdomain", "stjohns");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(hibernateFilter).setParameter("hospitalId", 2L);
        assertThat(chain.getRequest()).isNotNull();
    }

    private static Hospital hospitalWithId(Long id) throws Exception {
        Constructor<Hospital> constructor = Hospital.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Hospital hospital = constructor.newInstance();
        Field idField = Hospital.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(hospital, id);
        return hospital;
    }
}
