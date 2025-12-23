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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/health").permitAll()
                        // Me endpoint'leri token gerektirir (authenticated user için)
                        .requestMatchers("/api/auth/me").authenticated()
                        // Tag search, path, children ve create endpoint'leri authentication gerektirmez (public import için)
                        .requestMatchers("/api/tags/search").permitAll()
                        .requestMatchers("/api/tags/path").permitAll()
                        .requestMatchers("/api/tags/*/children").permitAll()
                        .requestMatchers("/api/tags/{id}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/tags").permitAll()  // Tag oluşturma için
                        .requestMatchers(HttpMethod.DELETE, "/api/tags/*").permitAll()  // Geçici: Tag silme için
                        // Product endpoint'leri authentication gerektirmez (test için, ileride admin kontrolü eklenecek)
                        .requestMatchers("/api/products/**").permitAll()

                        // Diğer her şey token ister (Trendyol import endpoint'i de authenticated kullanıcılar için)
                        .anyRequest().authenticated()
                )
                
                // Firebase JWT authentication filter'ı ekle
                .addFilterBefore(
                        firebaseAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
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
