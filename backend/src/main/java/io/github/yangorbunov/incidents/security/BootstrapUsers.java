package io.github.yangorbunov.incidents.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.config.AppProperties;

/**
 * Первый администратор создаётся из переменных окружения, а не из
 * миграции: хэш пароля в SQL-файле в репозитории — это пароль в репозитории.
 */
@Component
public class BootstrapUsers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUsers.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties properties;
    private final JdbcClient jdbc;

    public BootstrapUsers(AppUserRepository users, PasswordEncoder encoder, AppProperties properties, JdbcClient jdbc) {
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        var admin = properties.bootstrapAdmin();
        if (admin.password() == null || admin.password().isBlank()) {
            log.warn("Пользователей нет, а ADMIN_PASSWORD не задан — вход в систему невозможен");
            return;
        }
        users.insert(admin.username(), encoder.encode(admin.password()), Role.ADMIN, null);
        log.info("Создан администратор '{}'", admin.username());

        if (properties.demoUsers()) {
            // Аналитик из макетов ВКР — Петров П. В.
            Long petrov = jdbc.sql("SELECT id FROM employee WHERE external_id = 'u-petrov'")
                    .query(Long.class).optional().orElse(null);
            users.insert("petrov", encoder.encode("analyst-demo-pass"), Role.ANALYST, petrov);
            users.insert("viewer", encoder.encode("viewer-demo-pass"), Role.VIEWER, null);
            log.info("Созданы демо-пользователи petrov / viewer");
        }
    }
}
