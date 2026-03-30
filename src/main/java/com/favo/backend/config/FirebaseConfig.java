package com.favo.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final Environment environment;

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            InputStream serviceAccount;

            String base64Credentials = System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");

            if (base64Credentials != null && !base64Credentials.isBlank()) {
                byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                serviceAccount = new ByteArrayInputStream(decodedBytes);
            } else if (isLocalProfileActive()) {
                // Sadece local development: classpath'teki firebase/serviceAccountKey.json (gitignore'da)
                serviceAccount = getClass().getClassLoader()
                        .getResourceAsStream("firebase/serviceAccountKey.json");

                if (serviceAccount == null) {
                    throw new RuntimeException(
                            "Local profile active but firebase/serviceAccountKey.json not found on classpath. "
                                    + "Add src/main/resources/firebase/serviceAccountKey.json (never commit) "
                                    + "or set FIREBASE_SERVICE_ACCOUNT_BASE64.");
                }
            } else {
                throw new RuntimeException(
                        "Firebase credentials not found. Set FIREBASE_SERVICE_ACCOUNT_BASE64 environment variable "
                                + "(staging/production). File-based fallback is only allowed with spring.profiles.active=local.");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.initializeApp(options);
            }

            return FirebaseApp.getInstance();

        } catch (Exception e) {
            throw new RuntimeException("Firebase initialization failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isLocalProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "local".equalsIgnoreCase(p));
    }
}
