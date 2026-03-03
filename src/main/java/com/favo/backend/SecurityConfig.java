package com.favo.backend;

import com.favo.backend.Security.FirebaseAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CORS tamamen aktif - Flutter Web ve Railway dynamic domain desteği
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // CSRF devre dışı (stateless JWT kullanıyoruz)
                .csrf(csrf -> csrf.disable())
                
                // Stateless session yönetimi (JWT için gerekli)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Form login ve HTTP Basic devre dışı (JWT kullanıyoruz)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                
                // Endpoint yetkilendirme
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Login ve Register token gerektirmez (token'ı almak için kullanılıyor)
                        .requestMatchers("/api/auth/login", "/api/auth/login/admin", "/api/auth/register", "/api/auth/register/multipart", "/api/health").permitAll()
                        // WebSocket handshake endpoint'i - auth, WebSocketAuthInterceptor içinde yapılır
                        .requestMatchers("/ws/**").permitAll()
                        // Me endpoint'leri token gerektirir (authenticated user için - GET, PUT, DELETE)
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/me").authenticated()
                        // RBAC: Admin paneli endpoint'leri – sadece ROLE_ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Product yazma (create/update/delete) sadece admin
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        // Tag yazma (create/delete) sadece admin
                        .requestMatchers(HttpMethod.POST, "/api/tags").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tags/**").hasRole("ADMIN")
                        // Tag okuma public
                        .requestMatchers("/api/tags/search").permitAll()
                        .requestMatchers("/api/tags/path").permitAll()
                        .requestMatchers("/api/tags/*/children").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tags/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tags").permitAll()
                        // Product okuma public (GET)
                        .requestMatchers("/api/products/**").permitAll()
                        // My Reviews: sadece giriş yapmış kullanıcı kendi listesini alır
                        .requestMatchers(HttpMethod.GET, "/api/reviews/me").authenticated()
                        // Diğer Review GET endpoint'leri public (herkes görebilir)
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                        // Media GET endpoint'leri public (review'lar public olduğu için media'lar da public)
                        .requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()
                        // Review POST/PUT/DELETE endpoint'leri authenticated (sadece giriş yapmış kullanıcılar)
                        .requestMatchers(HttpMethod.POST, "/api/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reviews/**").authenticated()
                        // Interaction GET endpoint'leri public (like count vb. herkes görebilir)
                        .requestMatchers(HttpMethod.GET, "/api/interactions/**").permitAll()
                        // Interaction POST endpoint'leri authenticated (sadece giriş yapmış kullanıcılar like yapabilir)
                        .requestMatchers(HttpMethod.POST, "/api/interactions/**").authenticated()
                        // Messaging endpoint'leri: tümü authenticated kullanıcılar için
                        .requestMatchers("/api/messages/**").authenticated()
                        .requestMatchers("/ws-native/**").permitAll()

                        // Diğer her şey token ister (Trendyol import endpoint'i de authenticated kullanıcılar için)
                        .anyRequest().authenticated()
                )
                
                // Firebase JWT authentication filter'ı ekle
                // SecurityContextPersistenceFilter'dan sonra çalışmalı ki SecurityContext doğru set edilsin
                .addFilterAfter(
                        firebaseAuthenticationFilter,
                        SecurityContextHolderFilter.class
                )
                // Exception handling: Authentication başarısız olursa 401, authorization başarısız olursa 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                            response.getWriter().flush();
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                            response.getWriter().flush();
                        })
                )
                .build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Credentials izni (Authorization header için kritik)
        config.setAllowCredentials(true);
        
        // Railway dynamic domain ve Flutter Web için origin pattern'leri
        // NOT: allowCredentials(true) ile "*" kullanılamaz, bu yüzden pattern'ler kullanıyoruz
        // Environment variable'dan ekstra origin'ler okunabilir (virgülle ayrılmış)
        String additionalOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        List<String> originPatterns = new java.util.ArrayList<>(Arrays.asList(
                "http://localhost:*",
                "https://*.railway.app",
                "https://*.vercel.app",
                "https://*.web.app",
                "https://*.firebaseapp.com"
        ));
        
        // Environment variable'dan gelen ekstra origin'leri ekle
        if (additionalOrigins != null && !additionalOrigins.isBlank()) {
            originPatterns.addAll(Arrays.asList(additionalOrigins.split(",")));
        }
        
        config.setAllowedOriginPatterns(originPatterns);

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        config.setAllowedHeaders(List.of("*"));

        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return source;
    }
}
