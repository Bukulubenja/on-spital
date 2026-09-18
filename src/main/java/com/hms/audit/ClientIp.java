package com.hms.audit;

import jakarta.servlet.http.HttpServletRequest;

/** Direct port of hospital/views.py's _client_ip — leftmost X-Forwarded-For entry if behind a proxy, else the socket's remote address. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
