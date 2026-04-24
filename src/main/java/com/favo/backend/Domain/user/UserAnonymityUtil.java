package com.favo.backend.Domain.user;

import java.util.Locale;

public final class UserAnonymityUtil {

    private UserAnonymityUtil() {
    }

    public static boolean isAnonymous(SystemUser user) {
        return user != null && Boolean.TRUE.equals(user.getProfileAnonymous());
    }

    public static String maskedInitials(SystemUser user) {
        if (user == null) {
            return "A**** A****";
        }
        String first = maskPart(user.getName());
        String last = maskPart(user.getSurname());
        if (!first.isBlank() && !last.isBlank()) {
            return first + " " + last;
        }
        if (!first.isBlank()) {
            return first;
        }
        if (!last.isBlank()) {
            return last;
        }
        return maskPart(user.getUserName());
    }

    public static String publicUserName(SystemUser user) {
        if (user == null) return "User";
        if (isAnonymous(user)) return maskedInitials(user);
        if (user.getUserName() != null && !user.getUserName().isBlank()) {
            return user.getUserName().trim();
        }
        return "User";
    }

    public static String publicDisplayName(SystemUser user) {
        if (user == null) return "User";
        if (isAnonymous(user)) return maskedInitials(user);
        String first = user.getName() != null ? user.getName().trim() : "";
        String last = user.getSurname() != null ? user.getSurname().trim() : "";
        if (!first.isBlank() && !last.isBlank()) return first + " " + last;
        if (!first.isBlank()) return first;
        if (!last.isBlank()) return last;
        return publicUserName(user);
    }

    private static String maskPart(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        String initial = s.substring(0, 1).toUpperCase(Locale.ROOT);
        return initial + "****";
    }
}
