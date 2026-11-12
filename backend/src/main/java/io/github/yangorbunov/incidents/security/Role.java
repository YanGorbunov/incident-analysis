package io.github.yangorbunov.incidents.security;

/**
 * В ВКР пользователь один — аналитик. На практике нужен администратор
 * (учётные записи, справочники, аудит) и роль «только чтение» для
 * руководителей, которым нужны отчёты, но не правка данных.
 */
public enum Role {
    ADMIN,
    ANALYST,
    VIEWER;

    public String authority() {
        return "ROLE_" + name();
    }
}
