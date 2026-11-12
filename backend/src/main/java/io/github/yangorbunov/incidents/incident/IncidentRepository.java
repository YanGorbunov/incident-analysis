package io.github.yangorbunov.incidents.incident;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.yangorbunov.incidents.common.SqlParams;

import io.github.yangorbunov.incidents.common.PageResponse;
import io.github.yangorbunov.incidents.incident.IncidentDtos.CiRef;
import io.github.yangorbunov.incidents.incident.IncidentDtos.HistoryEntry;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentDetails;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentListItem;
import io.github.yangorbunov.incidents.incident.IncidentDtos.Source;

@Repository
public class IncidentRepository {

    /** Белый список сортировок: ключ из API → выражение SQL. */
    private static final Map<String, String> SORT = Map.of(
            "code", "i.code",
            "title", "i.title",
            "registeredAt", "i.registered_at",
            "closedAt", "i.closed_at",
            "priority", "p.sort_order",
            "status", "st.sort_order",
            "assignee", "assignee_name",
            "resolution", "resolution_seconds");

    public static final String LIST_SELECT = """
            SELECT i.id, i.code, i.external_id, i.title, i.registered_at, i.closed_at,
                   p.id AS priority_id, p.name AS priority_name, p.color AS priority_color,
                   st.id AS status_id, st.name AS status_name, st.phase AS status_phase,
                   e.id AS assignee_id,
                   CASE WHEN e.id IS NOT NULL THEN e.last_name || ' ' || left(e.first_name, 1) || '.'
                        || coalesce(' ' || left(e.middle_name, 1) || '.', '') END AS assignee_name,
                   (SELECT string_agg(c.name, ', ' ORDER BY c.name)
                    FROM incident_ci ic JOIN configuration_item c ON c.id = ic.ci_id
                    WHERE ic.incident_id = i.id) AS cis,
                   (SELECT string_agg(DISTINCT ins.name, ', ')
                    FROM incident_ci ic JOIN configuration_item c ON c.id = ic.ci_id
                    JOIN institution ins ON ins.id = c.institution_id
                    WHERE ic.incident_id = i.id) AS institutions,
                   coalesce(""" + IncidentSql.RESOLUTION_BREACHED + """
            , false) AS sla_breached,
                   extract(epoch FROM i.closed_at - i.registered_at)::bigint AS resolution_seconds
            """ + IncidentSql.BASE_FROM + """
            LEFT JOIN employee e ON e.id = i.assignee_id
            """;

    private final JdbcClient jdbc;

    public IncidentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<IncidentListItem> search(IncidentCriteria criteria, String sort, boolean desc,
                                                 int page, int size) {
        var where = IncidentSql.where(criteria);
        long total = jdbc.sql("SELECT count(*) " + IncidentSql.BASE_FROM + where.sql())
                .params(where.params()).query(Long.class).single();
        String order = SORT.getOrDefault(sort, "i.registered_at") + (desc ? " DESC NULLS LAST" : " ASC NULLS LAST");
        List<IncidentListItem> items = jdbc.sql(LIST_SELECT + where.sql()
                        + " ORDER BY " + order + ", i.id DESC LIMIT :limit OFFSET :offset")
                .params(where.params())
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(IncidentListItem.class)
                .list();
        return new PageResponse<>(items, total, page, size);
    }

    /** Все инциденты по критериям (для экспорта / снимка категории). */
    public List<Long> findIds(IncidentCriteria criteria) {
        var where = IncidentSql.where(criteria);
        return jdbc.sql("SELECT i.id " + IncidentSql.BASE_FROM + where.sql())
                .params(where.params()).query(Long.class).list();
    }

    public Optional<IncidentDetails> find(long id) {
        return jdbc.sql("SELECT * FROM incident WHERE id = :id").param("id", id)
                .query((rs, n) -> new IncidentDetails(
                        rs.getLong("id"), rs.getString("code"), rs.getString("external_id"),
                        Source.valueOf(rs.getString("source")), rs.getString("title"),
                        rs.getString("description"), rs.getString("resolution"),
                        rs.getObject("registered_at", OffsetDateTime.class),
                        rs.getObject("first_response_at", OffsetDateTime.class),
                        rs.getObject("resolved_at", OffsetDateTime.class),
                        rs.getObject("closed_at", OffsetDateTime.class),
                        rs.getLong("priority_id"), rs.getLong("status_id"),
                        (Long) rs.getObject("assignee_id"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getString("created_by"),
                        rs.getObject("updated_at", OffsetDateTime.class), rs.getString("updated_by"),
                        rs.getInt("version"), List.of(), List.of()))
                .optional()
                .map(d -> new IncidentDetails(d.id(), d.code(), d.externalId(), d.source(), d.title(),
                        d.description(), d.resolution(), d.registeredAt(), d.firstResponseAt(), d.resolvedAt(),
                        d.closedAt(), d.priorityId(), d.statusId(), d.assigneeId(), d.createdAt(), d.createdBy(),
                        d.updatedAt(), d.updatedBy(), d.version(), cis(id), history(id)));
    }

    public Optional<Long> findIdByExternalId(String externalId) {
        return jdbc.sql("SELECT id FROM incident WHERE external_id = :ext").param("ext", externalId)
                .query(Long.class).optional();
    }

    public List<CiRef> cis(long incidentId) {
        return jdbc.sql("""
                        SELECT c.id, c.code, c.name, ins.name AS institution_name
                        FROM incident_ci ic
                        JOIN configuration_item c ON c.id = ic.ci_id
                        JOIN institution ins ON ins.id = c.institution_id
                        WHERE ic.incident_id = :id ORDER BY c.name""")
                .param("id", incidentId).query(CiRef.class).list();
    }

    public List<HistoryEntry> history(long incidentId) {
        return jdbc.sql("""
                        SELECT h.changed_at, h.changed_by, sf.name AS from_status, s.name AS to_status
                        FROM incident_status_history h
                        LEFT JOIN status sf ON sf.id = h.from_status_id
                        JOIN status s ON s.id = h.to_status_id
                        WHERE h.incident_id = :id ORDER BY h.changed_at, h.id""")
                .param("id", incidentId).query(HistoryEntry.class).list();
    }

    public record Row(
            String code, String externalId, Source source, String title, String description, String resolution,
            OffsetDateTime registeredAt, OffsetDateTime firstResponseAt, OffsetDateTime resolvedAt,
            OffsetDateTime closedAt, long priorityId, long statusId, Long assigneeId, String user
    ) {
    }

    public long insert(Row r) {
        return jdbc.sql("""
                        INSERT INTO incident (code, external_id, source, title, description, resolution,
                                              registered_at, first_response_at, resolved_at, closed_at,
                                              priority_id, status_id, assignee_id, created_by, updated_by)
                        VALUES (:code, :externalId, :source, :title, :description, :resolution,
                                :registeredAt, :firstResponseAt, :resolvedAt, :closedAt,
                                :priorityId, :statusId, :assigneeId, :user, :user)
                        RETURNING id""")
                .paramSource(SqlParams.of(r))
                .query(Long.class)
                .single();
    }

    /** Оптимистичная блокировка: 0 строк — запись изменили параллельно. */
    public int update(long id, int version, Row r) {
        return jdbc.sql("""
                        UPDATE incident SET external_id = :externalId, title = :title, description = :description,
                               resolution = :resolution, registered_at = :registeredAt,
                               first_response_at = :firstResponseAt, resolved_at = :resolvedAt,
                               closed_at = :closedAt, priority_id = :priorityId, status_id = :statusId,
                               assignee_id = :assigneeId, updated_at = now(), updated_by = :user,
                               version = version + 1
                        WHERE id = :id AND version = :version""")
                .paramSource(SqlParams.of(r).addValue("id", id).addValue("version", version))
                .update();
    }

    public void replaceCis(long incidentId, List<Long> ciIds) {
        jdbc.sql("DELETE FROM incident_ci WHERE incident_id = :id").param("id", incidentId).update();
        for (Long ciId : ciIds.stream().distinct().toList()) {
            jdbc.sql("INSERT INTO incident_ci (incident_id, ci_id) VALUES (:i, :c)")
                    .param("i", incidentId).param("c", ciId).update();
        }
    }

    public void addHistory(long incidentId, Long fromStatusId, long toStatusId, OffsetDateTime at, String user) {
        jdbc.sql("""
                        INSERT INTO incident_status_history (incident_id, from_status_id, to_status_id,
                                                             changed_at, changed_by)
                        VALUES (:i, :from, :to, :at, :user)""")
                .param("i", incidentId)
                .param("from", fromStatusId)
                .param("to", toStatusId)
                .param("at", at)
                .param("user", user)
                .update();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM incident WHERE id = :id").param("id", id).update();
    }
}
