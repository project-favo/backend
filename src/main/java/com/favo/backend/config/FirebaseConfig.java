package com.favo.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            InputStream serviceAccount;
            
            // Önce environment variable'dan oku
            String base64Credentials = System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");
            
            if (base64Credentials != null && !base64Credentials.isBlank()) {
                // Environment variable'dan oku
                byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                serviceAccount = new ByteArrayInputStream(decodedBytes);
            } else {
                // Environment variable yoksa dosyadan oku
                serviceAccount = getClass().getClassLoader()
                        .getResourceAsStream("firebase/serviceAccountKey.json");
                
                if (serviceAccount == null) {
                    throw new RuntimeException(
                            "Firebase credentials not found. " +
                            "Either set FIREBASE_SERVICE_ACCOUNT_BASE64 environment variable " +
                            "or place serviceAccountKey.json in src/main/resources/firebase/");
                }
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
}
