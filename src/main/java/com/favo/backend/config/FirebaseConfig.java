package com.favo.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final Environment environment;

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            InputStream serviceAccount = resolveServiceAccountInputStream();

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

    /**
     * Base64 service account JSON. Railway: {@code FIREBASE_SERVICE_ACCOUNT_BASE64}.
     * Local: env, Spring Environment, veya {@code application-local.properties} (classpath veya
     * {@code src/main/resources/application-local.properties} — IDE gitignore yuzunden classpath'e
     * dusmeyebilir; bu yuzden dosyadan da okunur).
     */
    private InputStream resolveServiceAccountInputStream() {
        String base64Credentials = resolveFirebaseBase64();

        if (base64Credentials == null) {
            throw new RuntimeException(
                    "Firebase credentials not found. Set FIREBASE_SERVICE_ACCOUNT_BASE64 (Railway) or "
                            + "application-local.properties under src/main/resources/ or project root, with "
                            + "FIREBASE_SERVICE_ACCOUNT_BASE64=<base64> or firebase.service-account.base64=<base64>.");
        }

        byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
        return new java.io.ByteArrayInputStream(decodedBytes);
    }

    private String resolveFirebaseBase64() {
        String v = trimToNull(environment.getProperty("firebase.service-account.base64"));
        if (v != null) {
            return v;
        }
        v = trimToNull(environment.getProperty("FIREBASE_SERVICE_ACCOUNT_BASE64"));
        if (v != null) {
            return v;
        }
        v = trimToNull(System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64"));
        if (v != null) {
            return v;
        }

        v = readFirebaseFromPropertiesStream(classpathLocalProperties());
        if (v != null) {
            return v;
        }

        v = readFirebaseFromPropertiesStream(fileLocalPropertiesCandidates());
        if (v != null) {
            return v;
        }

        v = readFirebaseFromDotEnv();
        if (v != null) {
            return v;
        }

        return null;
    }

    /**
     * Spring Boot .env yüklemez; proje kökündeki {@code .env} dosyasından okur.
     * {@code KEY=value}, {@code export KEY=value}, {@code KEY = value} ve tırnaklı değer desteklenir.
     */
    private static String readFirebaseFromDotEnv() {
        String userDir = System.getProperty("user.dir", ".");
        Path base = Path.of(userDir).normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(base.resolve(".env"));
        Path parent = base.getParent();
        if (parent != null) {
            candidates.add(parent.resolve(".env"));
        }
        candidates.add(base.resolve("favo-backend").resolve(".env"));

        for (Path envFile : candidates) {
            if (!Files.isRegularFile(envFile)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (i == 0) {
                        line = stripUtf8Bom(line);
                    }
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith("export ")) {
                        line = line.substring(7).trim();
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if (val.length() >= 2
                            && ((val.startsWith("\"") && val.endsWith("\""))
                                    || (val.startsWith("'") && val.endsWith("'")))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    if ("FIREBASE_SERVICE_ACCOUNT_BASE64".equals(key)
                            || "firebase.service-account.base64".equals(key)) {
                        return trimToNull(val);
                    }
                }
            } catch (IOException ignored) {
                // try next path
            }
        }
        return null;
    }

    private static String stripUtf8Bom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static InputStream classpathLocalProperties() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            return null;
        }
        return cl.getResourceAsStream("application-local.properties");
    }

    private static InputStream fileLocalPropertiesCandidates() {
        String userDir = System.getProperty("user.dir", ".");
        Path[] relative = new Path[] {
                Path.of("src", "main", "resources", "application-local.properties"),
                Path.of("application-local.properties"),
                Path.of("config", "application-local.properties")
        };
        for (Path rel : relative) {
            Path abs = Path.of(userDir).resolve(rel).normalize();
            if (Files.isRegularFile(abs)) {
                try {
                    return Files.newInputStream(abs);
                } catch (IOException ignored) {
                    // try next
                }
            }
        }
        return null;
    }

    private static String readFirebaseFromPropertiesStream(InputStream in) {
        if (in == null) {
            return null;
        }
        try (InputStream stream = in;
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            String fromKey = trimToNull(p.getProperty("FIREBASE_SERVICE_ACCOUNT_BASE64"));
            if (fromKey != null) {
                return fromKey;
            }
            return trimToNull(p.getProperty("firebase.service-account.base64"));
        } catch (IOException e) {
            return null;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
