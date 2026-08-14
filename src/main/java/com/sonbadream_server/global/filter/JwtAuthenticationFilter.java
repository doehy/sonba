package com.sonbadream_server.global.filter;

import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import com.sonbadream_server.global.common.CommonResponse;
import com.sonbadream_server.global.exception.ErrorCode;
import com.sonbadream_server.global.exception.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

@Log4j2
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final Set<String> FILTER_SKIP_PATHS = Set.of(
            "/auth/refresh"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object tokenType = claims.get("type");
            if (!"access".equals(tokenType)) {
                writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
                return;
            }

            Long userId = Long.valueOf(claims.getSubject());

            User user = userRepository.findById(userId)
                    .orElse(null);
            if (user == null) {
                writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
                return;
            }

            if (user.getStatus() == UserStatus.WITHDRAWN && !isLogoutRequest(request)) {
                writeErrorResponse(response, ErrorCode.USER_WITHDRAWN);
                return;
            }
            if (user.getStatus() == UserStatus.SUSPENDED && !isLogoutRequest(request)) {
                writeErrorResponse(response, ErrorCode.USER_SUSPENDED);
                return;
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            user.getUserId(),   // 또는 CustomPrincipal
                            null,
                            Collections.emptyList()
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);


        } catch (JwtException | IllegalArgumentException e) {
            writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return FILTER_SKIP_PATHS.contains(request.getRequestURI());
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return "/auth/logout".equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());
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
