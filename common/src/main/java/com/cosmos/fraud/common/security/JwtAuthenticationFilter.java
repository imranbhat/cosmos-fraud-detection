package com.cosmos.fraud.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> SKIP_PATHS = List.of("/actuator/**", "/v1/health");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return SKIP_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            String subject = validateAndExtractSubject(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String validateAndExtractSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT structure");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])));

        String subject = extractClaim(payloadJson, "sub");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT missing subject claim");
        }

        long exp = extractExpiry(payloadJson);
        if (exp > 0 && exp < System.currentTimeMillis() / 1000) {
            throw new IllegalArgumentException("JWT token has expired");
        }

        return subject;
    }

    private String extractClaim(String payloadJson, String claimName) {
        String search = "\"" + claimName + "\"";
        int idx = payloadJson.indexOf(search);
        if (idx == -1) return null;

        int colonIdx = payloadJson.indexOf(':', idx + search.length());
        if (colonIdx == -1) return null;

        int valueStart = colonIdx + 1;
        while (valueStart < payloadJson.length() && payloadJson.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (payloadJson.charAt(valueStart) == '"') {
            int valueEnd = payloadJson.indexOf('"', valueStart + 1);
            return payloadJson.substring(valueStart + 1, valueEnd);
        }

        int valueEnd = valueStart;
        while (valueEnd < payloadJson.length()
                && payloadJson.charAt(valueEnd) != ','
                && payloadJson.charAt(valueEnd) != '}') {
            valueEnd++;
        }
        return payloadJson.substring(valueStart, valueEnd).trim();
    }

    private long extractExpiry(String payloadJson) {
        String expStr = extractClaim(payloadJson, "exp");
        if (expStr == null) return -1;
        try {
            return Long.parseLong(expStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String padBase64(String base64) {
        int remainder = base64.length() % 4;
        if (remainder == 0) return base64;
        return base64 + "=".repeat(4 - remainder);
    }
}
