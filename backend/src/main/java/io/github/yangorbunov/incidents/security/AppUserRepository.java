package io.github.yangorbunov.incidents.security;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AppUserRepository {

    public record AppUser(
            long id,
            String username,
            String passwordHash,
            Role role,
            Long employeeId,
            boolean enabled,
            String totpSecret,
            int failedAttempts,
            OffsetDateTime lockedUntil,
            OffsetDateTime createdAt
    ) {
    }

    /** Пользователь для API: без хэша и секрета. */
    public record UserView(
            long id,
            String username,
            Role role,
            boolean enabled,
            boolean totpEnabled,
            OffsetDateTime lockedUntil,
            Long employeeId,
            String employeeName,
            String postName,
            OffsetDateTime createdAt
    ) {
    }

    private static final String VIEW_SELECT = """
            SELECT u.id, u.username, u.role, u.enabled, u.totp_secret IS NOT NULL AS totp_enabled,
                   u.locked_until, u.employee_id,
                   CASE WHEN e.id IS NOT NULL THEN e.last_name || ' ' || e.first_name END AS employee_name,
                   p.name AS post_name, u.created_at
            FROM app_user u
            LEFT JOIN employee e ON e.id = u.employee_id
            LEFT JOIN post p ON p.id = e.post_id
            """;

    private final JdbcClient jdbc;

    public AppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.sql("SELECT * FROM app_user WHERE lower(username) = lower(:u)")
                .param("u", username)
                .query(AppUser.class)
                .optional();
    }

    public Optional<AppUser> findById(long id) {
        return jdbc.sql("SELECT * FROM app_user WHERE id = :id").param("id", id).query(AppUser.class).optional();
    }

    public List<UserView> findAllViews() {
        return jdbc.sql(VIEW_SELECT + " ORDER BY u.username").query(UserView.class).list();
    }

    public Optional<UserView> findView(String username) {
        return jdbc.sql(VIEW_SELECT + " WHERE lower(u.username) = lower(:u)")
                .param("u", username).query(UserView.class).optional();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single();
    }

    public long insert(String username, String passwordHash, Role role, Long employeeId) {
        return jdbc.sql("""
                        INSERT INTO app_user (username, password_hash, role, employee_id)
                        VALUES (:username, :hash, :role, :employeeId) RETURNING id
                        """)
                .param("username", username)
                .param("hash", passwordHash)
                .param("role", role.name())
                .param("employeeId", employeeId)
                .query(Long.class)
                .single();
    }

    public void update(long id, Role role, boolean enabled, Long employeeId) {
        jdbc.sql("UPDATE app_user SET role = :role, enabled = :enabled, employee_id = :employeeId WHERE id = :id")
                .param("role", role.name())
                .param("enabled", enabled)
                .param("employeeId", employeeId)
                .param("id", id)
                .update();
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.sql("UPDATE app_user SET password_hash = :h, failed_attempts = 0, locked_until = NULL WHERE id = :id")
                .param("h", passwordHash).param("id", id).update();
    }

    public void updateTotpSecret(long id, String secret) {
        jdbc.sql("UPDATE app_user SET totp_secret = :s WHERE id = :id").param("s", secret).param("id", id).update();
    }

    public void registerFailure(long id, int maxAttempts, OffsetDateTime lockUntil) {
        jdbc.sql("""
                        UPDATE app_user
                        SET failed_attempts = failed_attempts + 1,
                            locked_until = CASE WHEN failed_attempts + 1 >= :max THEN :lockUntil ELSE locked_until END
                        WHERE id = :id
                        """)
                .param("max", maxAttempts)
                .param("lockUntil", lockUntil)
                .param("id", id)
                .update();
    }

    public void resetFailures(long id) {
        jdbc.sql("UPDATE app_user SET failed_attempts = 0, locked_until = NULL WHERE id = :id")
                .param("id", id).update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", id).update();
    }
}
