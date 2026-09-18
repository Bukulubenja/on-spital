package com.hms.security;

import com.hms.tenancy.TenantResolvingFilter;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Comma-separated allowed origins for browser clients (e.g. hms-web's
    // Vite dev server). Empty by default so non-browser deployments don't
    // silently open CORS; the frontend dev workflow sets this explicitly.
    @Value("${app.cors-allowed-origins:}")
    private String corsAllowedOrigins;

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

    /**
     * Off by default (empty origin list = Spring Security's CORS filter
     * matches nothing, so browsers get no Access-Control-Allow-Origin and
     * fail preflight). hms-web (the staff admin frontend, a separate
     * project) sets APP_CORS_ALLOWED_ORIGINS=http://localhost:5173 for
     * local dev. Credentials must stay enabled — Basic Auth is carried in
     * the Authorization header, which fetch() only attaches cross-origin
     * when credentials are allowed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                corsAllowedOrigins.isBlank() ? List.of() : List.of(corsAllowedOrigins.split(","))
        );
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Hospital-Subdomain"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OpenEntityManagerInViewFilter openEntityManagerInViewFilter,
            TenantResolvingFilter tenantResolvingFilter,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
