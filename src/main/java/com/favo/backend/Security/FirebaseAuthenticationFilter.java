package com.favo.backend.Security;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Firebase.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
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

        // Login, Register, Health, Tag Search ve Product endpointleri serbest: burada token zorlamıyoruz
        // Me endpoint'leri token gerektirir (yukarıda SecurityConfig'de authenticated() olarak işaretlendi)
        if (path.equals("/api/auth/login") || 
            path.equals("/api/auth/register") || 
            path.equals("/api/health") ||
            path.startsWith("/api/tags/search") ||
            path.startsWith("/api/products")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // token yok -> SecurityConfig "authenticated()" dediği için sonra 401 dönecek
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        try {
            // 🔥 burada sadece LOGIN çalışır: verify + DB'de aktif user bul
            SystemUser user = authService.login(token);

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

        } catch (Exception ex) {
            // token invalid / kullanıcı pasif vs.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + ex.getMessage() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}