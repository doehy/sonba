package com.sonbadream_server.global.filter;

import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.global.common.CommonResponse;
import com.sonbadream_server.global.exception.ErrorCode;
import com.sonbadream_server.global.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AdminVerificationFilter extends OncePerRequestFilter {
    public static final String ADMIN_VERIFIED_TOKEN_HEADER = "X-Admin-Verified-Token";
    private static final String ADMIN_VERIFIED_TOKEN_TYPE = "admin_verified";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        if (!UserRole.ADMIN.equals(user.getRole())) {
            writeErrorResponse(response, ErrorCode.FORBIDDEN);
            return;
        }

        String adminVerifiedToken = request.getHeader(ADMIN_VERIFIED_TOKEN_HEADER);
        if (adminVerifiedToken == null || adminVerifiedToken.isBlank()
                || !isValidAdminVerifiedToken(adminVerifiedToken, userId)) {
            writeErrorResponse(response, ErrorCode.ADMIN_VERIFICATION_REQUIRED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !uri.startsWith("/admin/")
                || "/admin/verify".equals(uri);
    }

    private boolean isValidAdminVerifiedToken(String token, Long currentUserId) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return ADMIN_VERIFIED_TOKEN_TYPE.equals(claims.get("type"))
                    && currentUserId.toString().equals(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(CommonResponse.error(errorResponse)));
    }
}
