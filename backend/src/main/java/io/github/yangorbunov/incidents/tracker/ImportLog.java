package io.github.yangorbunov.incidents.tracker;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Журнал импортов. Пишется в отдельной транзакции: неудачный импорт
 * откатывается целиком, но запись о том, что он был и почему упал, остаётся.
 */
@Component
public class ImportLog {

    public enum Kind { INCIDENT, CI }

    public enum Outcome { CREATED, UPDATED, NOT_FOUND, FAILED }

    public record ImportRun(long id, Kind kind, String externalId, Outcome outcome, String message, Long entityId,
                            String requestedBy, OffsetDateTime startedAt, Long durationMs) {
    }

    private final JdbcClient jdbc;

    public ImportLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Kind kind, String externalId, Outcome outcome, String message, Long entityId,
                       String user, long durationMs) {
        jdbc.sql("""
                        INSERT INTO import_run (kind, external_id, outcome, message, entity_id, requested_by, duration_ms)
                        VALUES (:kind, :ext, :outcome, :message, :entityId, :user, :duration)""")
                .param("kind", kind.name())
                .param("ext", externalId)
                .param("outcome", outcome.name())
                .param("message", message)
                .param("entityId", entityId)
                .param("user", user)
                .param("duration", durationMs)
                .update();
    }

    public List<ImportRun> recent(int limit) {
        return jdbc.sql("SELECT * FROM import_run ORDER BY started_at DESC, id DESC LIMIT :limit")
                .param("limit", limit).query(ImportRun.class).list();
    }
}
