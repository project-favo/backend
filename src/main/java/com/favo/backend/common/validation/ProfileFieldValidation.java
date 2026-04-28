package com.favo.backend.common.validation;

import com.favo.backend.common.error.FavoException;
import com.favo.backend.common.error.UserErrorCode;

/**
 * Kayıt ve profil güncellemede ad / soyad / kullanıcı adı uzunluk kontrolleri.
 */
public final class ProfileFieldValidation {

    private ProfileFieldValidation() {
    }

    public static void validateUserNameLength(String trimmed) {
        if (trimmed.length() > ProfileFieldLimits.MAX_USERNAME_LENGTH) {
            throw new FavoException(UserErrorCode.PROFILE_USERNAME_TOO_LONG);
        }
    }

    public static void validateFirstNameLength(String trimmedOrEmpty) {
        if (trimmedOrEmpty != null && !trimmedOrEmpty.isEmpty()
                && trimmedOrEmpty.length() > ProfileFieldLimits.MAX_FIRST_NAME_LENGTH) {
            throw new FavoException(UserErrorCode.PROFILE_FIRST_NAME_TOO_LONG);
        }
    }

    public static void validateLastNameLength(String trimmedOrEmpty) {
        if (trimmedOrEmpty != null && !trimmedOrEmpty.isEmpty()
                && trimmedOrEmpty.length() > ProfileFieldLimits.MAX_LAST_NAME_LENGTH) {
            throw new FavoException(UserErrorCode.PROFILE_LAST_NAME_TOO_LONG);
        }
    }

    /**
     * Tam kayıt: kullanıcı adı zorunlu ve dolu; ad/soyad boş olamaz (mobil ile uyumlu).
     */
    public static void validateRegistrationNames(String name, String surname) {
        String n = name == null ? "" : name.trim();
        String s = surname == null ? "" : surname.trim();
        validateFirstNameLength(n);
        validateLastNameLength(s);
    }
}
