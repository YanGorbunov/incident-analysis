package io.github.yangorbunov.incidents.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.github.yangorbunov.incidents.common.PageResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Журнал событий (п. 3.4 ВКР: «ведение детализированных журналов всех
 * событий, включая попытки входа и изменения информации»).
 * Пишется в той же транзакции, что и изменение: нет изменения — нет записи.
 */
@Service
public class AuditService {

    public record AuditEvent(
            long id,
            OffsetDateTime occurredAt,
            String username,
            String action,
            String entityType,
            String entityId,
            String details,
            String remoteAddr
    ) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void log(String action, String entityType, Object entityId, Map<String, ?> details) {
        log(currentUsername(), action, entityType, entityId, details);
    }

    public void log(String username, String action, String entityType, Object entityId, Map<String, ?> details) {
        jdbc.sql("""
                        INSERT INTO audit_event (username, action, entity_type, entity_id, details, remote_addr)
                        VALUES (:username, :action, :entityType, :entityId, CAST(:details AS jsonb), :addr)
                        """)
                .param("username", username)
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId == null ? null : String.valueOf(entityId))
                .param("details", details == null || details.isEmpty() ? null : json.writeValueAsString(details))
                .param("addr", remoteAddr())
                .update();
    }

    public PageResponse<AuditEvent> find(String username, String action, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        var params = new java.util.HashMap<String, Object>();
        if (username != null && !username.isBlank()) {
            where.append(" AND username ILIKE :username");
            params.put("username", "%" + username.strip() + "%");
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = :action");
            params.put("action", action);
        }
        long total = jdbc.sql("SELECT count(*) FROM audit_event" + where).params(params).query(Long.class).single();
        List<AuditEvent> items = jdbc.sql("""
                        SELECT id, occurred_at, username, action, entity_type, entity_id,
                               details::text AS details, remote_addr
                        FROM audit_event""" + where + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(AuditEvent.class)
                .list();
        return new PageResponse<>(items, total, page, size);
    }

    public List<String> actions() {
        return jdbc.sql("SELECT DISTINCT action FROM audit_event ORDER BY 1").query(String.class).list();
    }

    static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static String remoteAddr() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
