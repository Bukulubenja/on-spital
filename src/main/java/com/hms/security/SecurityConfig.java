package com.hms.security;

import com.hms.tenancy.TenantResolvingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DjangoPbkdf2PasswordEncoder();
    }

    /**
     * spring.jpa.open-in-view=true only auto-registers an MVC
     * *interceptor*, which runs inside DispatcherServlet — after the
     * Servlet filter chain (where TenantResolvingFilter lives) has already
     * run. Without this, TenantResolvingFilter's entityManager.unwrap(...)
     * would create-and-immediately-close a throwaway persistence context,
     * so the tenantFilter it enables would never be the one actually used
     * by the controller/lazy loads. This real Filter binds the session for
     * the whole request, including everything before DispatcherServlet.
     * (Found by live end-to-end testing — see git history.)
     */
    @Bean
    public OpenEntityManagerInViewFilter openEntityManagerInViewFilter() {
        return new OpenEntityManagerInViewFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OpenEntityManagerInViewFilter openEntityManagerInViewFilter,
            TenantResolvingFilter tenantResolvingFilter
    ) throws Exception {
        http
                // Stateless API for now (Phase 1 is a verification endpoint,
                // not a browser session) — no CSRF token flow to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Order matters: session-binding filter first, then tenant
                // resolution (needs that session to enable its Hibernate
                // filter), then Basic auth (whose UserDetailsService lookup
                // needs the tenant filter already enabled) — same ordering
                // requirement TenantMiddleware has ahead of
                // AuthenticationMiddleware in Django.
                //
                // TenantResolvingFilter must be registered (relative to a
                // well-known filter) before it can itself be used as an
                // anchor below — Spring Security's FilterOrderRegistration
                // only recognizes a custom filter class once some
                // addFilter*() call has placed it in the chain.
                .addFilterBefore(tenantResolvingFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(openEntityManagerInViewFilter, TenantResolvingFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
