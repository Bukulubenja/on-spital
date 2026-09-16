package com.hms.web;

import com.hms.security.HmsUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves tenant resolution + auth work together end to end against real
 * HMS data — Phase 1's only endpoint. No business logic belongs here;
 * this goes away once a real workflow (Phase 2) has its own endpoints.
 */
@RestController
public class WhoAmIController {

    @GetMapping("/api/whoami")
    public WhoAmIResponse whoami(@AuthenticationPrincipal HmsUserPrincipal principal) {
        var user = principal.getUser();
        String subdomain = user.getHospital() == null ? null : user.getHospital().getSubdomain();
        return new WhoAmIResponse(user.getUsername(), user.getRole().name(), subdomain);
    }

    public record WhoAmIResponse(String username, String role, String hospitalSubdomain) {
    }
}
