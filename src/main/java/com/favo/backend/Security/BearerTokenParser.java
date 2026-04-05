package com.favo.backend.Security;

/**
 * Authorization header: case-insensitive "Bearer &lt;token&gt;" ayrıştırması.
 */
public final class BearerTokenParser {

    private static final String PREFIX = "Bearer ";

    private BearerTokenParser() {
    }

    public static boolean hasBearer(String authorization) {
        if (authorization == null) {
            return false;
        }
        String h = authorization.trim();
        return h.length() > PREFIX.length() && h.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /**
     * Bearer yoksa veya boşsa boş string döner.
     */
    public static String extractToken(String authorization) {
        if (!hasBearer(authorization)) {
            return "";
        }
        return authorization.trim().substring(PREFIX.length()).trim();
    }
}
