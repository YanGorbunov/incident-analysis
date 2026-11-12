package io.github.yangorbunov.incidents.security;

import io.github.yangorbunov.incidents.common.ValidationException;

/**
 * «Уникальный пароль высокой сложности» из п. 3.4 ВКР, сформулированный
 * проверяемо. Длина важнее «спецсимволов» (NIST SP 800-63B), поэтому
 * минимум 12 символов и запрет очевидных вариантов.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {
    }

    public static void check(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ValidationException("Пароль должен содержать не менее " + MIN_LENGTH + " символов");
        }
        if (password.length() > 128) {
            throw new ValidationException("Пароль слишком длинный");
        }
        String lower = password.toLowerCase();
        if (username != null && lower.contains(username.toLowerCase())) {
            throw new ValidationException("Пароль не должен содержать логин");
        }
        if (password.chars().distinct().count() < 5) {
            throw new ValidationException("Пароль слишком простой");
        }
    }
}
