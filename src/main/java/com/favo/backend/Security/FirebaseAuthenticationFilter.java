package com.favo.backend.Security;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Firebase.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // CORS preflight (OPTIONS) isteklerinde Firebase doğrulaması yapmadan devam et
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String header = request.getHeader("Authorization");
        boolean hasToken = header != null && header.startsWith("Bearer ");

        // Login ve Register endpoint'leri token kontrolü yapmadan direkt geç (token'ı controller içinde handle ediyoruz)
        // Register endpoint'inde kullanıcı henüz DB'de yok, bu yüzden authentication yapmamalıyız
        // startsWith kullanarak hem /api/auth/register hem de /api/auth/register/multipart'i yakalıyoruz
        boolean isAuthEndpoint = path.equals("/api/auth/login") || 
                                 path.startsWith("/api/auth/register");
        
        if (isAuthEndpoint) {
            log.debug("Auth endpoint detected (login/register), skipping authentication filter: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Health, Tag Search/Path/Children/Create ve Product endpointleri serbest: burada token zorlamıyoruz
        // Me endpoint'leri token gerektirir (yukarıda SecurityConfig'de authenticated() olarak işaretlendi)
        // GET endpoint'leri public (SecurityConfig'de permitAll() olarak işaretlendi) ama token varsa authentication yapılmalı
        boolean isPublicEndpoint = path.equals("/api/health") ||
            path.startsWith("/api/tags/search") ||
            path.startsWith("/api/tags/path") ||
            (path.equals("/api/tags") && "POST".equalsIgnoreCase(request.getMethod())) ||  // POST /api/tags
            (path.matches("/api/tags/\\d+") && "DELETE".equalsIgnoreCase(request.getMethod())) ||  // DELETE /api/tags/{id}
            path.matches("/api/tags/\\d+/children") ||  // /api/tags/{id}/children
            path.startsWith("/api/products") ||
            (path.startsWith("/api/reviews") && "GET".equalsIgnoreCase(request.getMethod())) ||  // GET /api/reviews/** (public)
            (path.startsWith("/api/interactions") && "GET".equalsIgnoreCase(request.getMethod()));  // GET /api/interactions/** (public)

        // Public endpoint'ler için token yoksa direkt geç, token varsa authentication yap
        if (isPublicEndpoint && !hasToken) {
            filterChain.doFilter(request, response);
            return;
        }

        // Token yoksa ve endpoint authenticated gerektiriyorsa -> SecurityConfig "authenticated()" dediği için sonra 401 dönecek
        if (!hasToken) {
            log.debug("No Authorization header found for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String token = header != null ? header.substring(7).trim() : "";
        log.info("Processing authentication for path: {}", path);

        try {
            // 🔥 burada sadece LOGIN çalışır: verify + DB'de aktif user bul
            SystemUser user = authService.login(token);
            log.info("User authenticated successfully: userId={}, firebaseUid={}, isActive={}", 
                user.getId(), user.getFirebaseUid(), user.getIsActive());

            // authority'yi userType üzerinden bağla (ör: ROLE_USER / ROLE_ADMIN)
            String roleName = (user.getUserType() != null && user.getUserType().getName() != null)
                    ? user.getUserType().getName()
                    : "ROLE_USER";

            var auth = new FirebaseAuthenticationToken(
                    user,
                    user.getFirebaseUid(),
                    List.of(new SimpleGrantedAuthority(roleName))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.info("Authentication set in SecurityContext for user: {}, role: {}", user.getId(), roleName);
            
            // SecurityContext'in doğru set edildiğini kontrol et
            var contextAuth = SecurityContextHolder.getContext().getAuthentication();
            if (contextAuth == null || !contextAuth.isAuthenticated()) {
                log.error("SecurityContext authentication is null or not authenticated after setting!");
            } else {
                log.info("SecurityContext authentication verified: authenticated={}, principal={}", 
                    contextAuth.isAuthenticated(), 
                    contextAuth.getPrincipal() != null ? contextAuth.getPrincipal().getClass().getSimpleName() : "NULL");
            }

        } catch (Exception ex) {
            // token invalid / kullanıcı pasif vs.
            log.error("Authentication failed for path: {} - Error: {}", path, ex.getMessage(), ex);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try {
                response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + 
                    ex.getMessage().replace("\"", "\\\"") + "\"}");
                response.getWriter().flush();
            } catch (IOException e) {
                log.error("Failed to write error response", e);
            }   
            return;
        }

        filterChain.doFilter(request, response);
        
        // Filter chain'den sonra authentication'ın hala var olup olmadığını kontrol et
        var afterAuth = SecurityContextHolder.getContext().getAuthentication();
        if (afterAuth == null || !afterAuth.isAuthenticated()) {
            log.warn("SecurityContext authentication lost after filter chain! Path: {}", path);
        }
    }
}