package com.favo.backend.Security;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Firebase.AuthService;
import com.favo.backend.Service.Firebase.FirebaseAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final AuthService authService;
    private final FirebaseAuthService firebaseAuthService;

    public FirebaseAuthenticationFilter(AuthService authService, FirebaseAuthService firebaseAuthService) {
        this.authService = authService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = normalizedServletPath(request);
        String method = request.getMethod();
        String header = request.getHeader("Authorization");
        boolean hasToken = BearerTokenParser.hasBearer(header);

        boolean isRegisterVerify = "POST".equalsIgnoreCase(method) && path.equals("/api/auth/register/verify");
        if (isRegisterVerify) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isPreAccountRegister = "POST".equalsIgnoreCase(method)
                && (path.equals("/api/auth/register") || path.equals("/api/auth/register/multipart"));
        if (isPreAccountRegister) {
            if (!hasToken) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try {
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                } catch (IOException e) {
                    log.error("Failed to write error response", e);
                }
                return;
            }
            try {
                String token = BearerTokenParser.extractToken(header);
                FirebaseUserInfo info = firebaseAuthService.verify(token);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(info, "firebase", Collections.emptyList());
                auth.setAuthenticated(true);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Pre-account Firebase user verified: uid={}", info.getUid());
            } catch (Exception ex) {
                log.error("Pre-account Firebase token verification failed: {}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                String msg = ex.getMessage() != null ? ex.getMessage().replace("\"", "\\\"") : "";
                try {
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + msg + "\"}");
                } catch (IOException e) {
                    log.error("Failed to write error body", e);
                }
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAuthEndpoint = path.equals("/api/auth/login")
                || path.equals("/api/auth/login/admin")
                || path.equals("/api/auth/verify-email")
                || path.equals("/api/auth/resend-verification")
                || path.equals("/api/auth/forgot-password");

        if (isAuthEndpoint) {
            log.debug("Auth endpoint, skip filter: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        boolean isProfileImageGet = "GET".equalsIgnoreCase(request.getMethod())
                && path.contains("/api/users/")
                && path.endsWith("/profile-image");

        boolean isPublicEndpoint = path.equals("/api/health") || path.contains("/api/health")
                || path.equals("/api/internal/catalog-import-from-json")
                || path.contains("/api/tags/search")
                || path.contains("/api/tags/path")
                || (path.endsWith("/api/tags") && "POST".equalsIgnoreCase(request.getMethod()))
                || (path.matches(".*/api/tags/\\d+") && "DELETE".equalsIgnoreCase(request.getMethod()))
                || path.matches(".*/api/tags/\\d+/children")
                || path.contains("/api/products")
                || (path.contains("/api/reviews") && "GET".equalsIgnoreCase(request.getMethod()))
                || (path.contains("/api/interactions") && "GET".equalsIgnoreCase(request.getMethod()))
                || isProfileImageGet;

        if (isPublicEndpoint) {
            if (!hasToken) {
                filterChain.doFilter(request, response);
                return;
            }

            String publicPathToken = BearerTokenParser.extractToken(header);
            try {
                SystemUser user = authService.login(publicPathToken);
                String roleName = resolveRoleName(user);

                FirebaseAuthenticationToken auth = new FirebaseAuthenticationToken(
                        user,
                        user.getFirebaseUid(),
                        Collections.singletonList(new SimpleGrantedAuthority(roleName))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Optional auth OK for public path: {}", path);
            } catch (Exception ex) {
                log.debug("Optional auth skipped for public path: {}", path);
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasToken) {
            log.debug("No Authorization for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String bearerToken = BearerTokenParser.extractToken(header);
        log.info("Processing authentication for path: {}", path);

        try {
            SystemUser user = authService.login(bearerToken);
            log.info("User authenticated: userId={}, firebaseUid={}, isActive={}",
                    user.getId(), user.getFirebaseUid(), user.getIsActive());

            String roleName = resolveRoleName(user);

            FirebaseAuthenticationToken auth = new FirebaseAuthenticationToken(
                    user,
                    user.getFirebaseUid(),
                    Collections.singletonList(new SimpleGrantedAuthority(roleName))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.info("SecurityContext set for user: {}, role: {}", user.getId(), roleName);

            Authentication contextAuth = SecurityContextHolder.getContext().getAuthentication();
            if (contextAuth == null || !contextAuth.isAuthenticated()) {
                log.error("SecurityContext not authenticated after set");
            } else {
                String principalName = "none";
                if (contextAuth.getPrincipal() != null) {
                    principalName = contextAuth.getPrincipal().getClass().getSimpleName();
                }
                log.info("SecurityContext OK: authenticated={}, principal={}",
                        contextAuth.isAuthenticated(), principalName);
            }

        } catch (Exception ex) {
            log.error("Authentication failed for path: {} - {}", path, ex.getMessage(), ex);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try {
                String msg = ex.getMessage() != null ? ex.getMessage().replace("\"", "\\\"") : "";
                response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + msg + "\"}");
                response.getWriter().flush();
            } catch (IOException e) {
                log.error("Failed to write error response", e);
            }
            return;
        }

        filterChain.doFilter(request, response);

        Authentication afterAuth = SecurityContextHolder.getContext().getAuthentication();
        if (afterAuth == null || !afterAuth.isAuthenticated()) {
            log.warn("SecurityContext lost after chain, path: {}", path);
        }
    }

    private static String resolveRoleName(SystemUser user) {
        if (user.getUserType() != null && user.getUserType().getName() != null) {
            return user.getUserType().getName();
        }
        return SecurityRoles.ROLE_USER;
    }

    /**
     * Context-path (ör. /backend) veya boşluk sonrası /api/... ile tutarlı eşleşme için.
     */
    private static String normalizedServletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri != null && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        // Reverse proxy / ek path öneki: .../api/auth/... şeklinde /api'den kes
        if (!uri.startsWith("/api/")) {
            int authIdx = uri.indexOf("/api/auth/");
            if (authIdx >= 0) {
                uri = uri.substring(authIdx);
            }
        }
        return uri;
    }
}
