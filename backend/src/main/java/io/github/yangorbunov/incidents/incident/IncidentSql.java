package io.github.yangorbunov.incidents.incident;

import java.util.HashMap;
import java.util.Map;

/**
 * Построение SQL по {@link IncidentCriteria}. Значения — только через
 * именованные параметры; в текст запроса попадают лишь фрагменты из кода.
 * <p>
 * Ожидается, что в FROM есть {@code incident i}, {@code priority p},
 * {@code status st}.
 */
public final class IncidentSql {

    /** Нарушение SLA решения: до resolved_at (или closed_at), а для незакрытых — до текущего момента. */
    public static final String RESOLUTION_BREACHED = """
            (p.resolution_sla_minutes IS NOT NULL
             AND coalesce(i.resolved_at, i.closed_at,
                          CASE WHEN st.phase IN ('NEW', 'ACTIVE') THEN now() END)
                 - i.registered_at > make_interval(mins => p.resolution_sla_minutes))""";

    /** SLA решения применим, если он задан и момент решения известен (или инцидент ещё открыт). */
    public static final String RESOLUTION_APPLICABLE = """
            (p.resolution_sla_minutes IS NOT NULL
             AND (i.resolved_at IS NOT NULL OR i.closed_at IS NOT NULL OR st.phase IN ('NEW', 'ACTIVE')))""";

    /** Нарушение SLA реакции. Если реакция не зафиксирована, а инцидент уже не новый — момент неизвестен. */
    public static final String RESPONSE_BREACHED = """
            (p.response_sla_minutes IS NOT NULL
             AND coalesce(i.first_response_at, CASE WHEN st.phase = 'NEW' THEN now() END)
                 - i.registered_at > make_interval(mins => p.response_sla_minutes))""";

    public static final String RESPONSE_APPLICABLE = """
            (p.response_sla_minutes IS NOT NULL
             AND (i.first_response_at IS NOT NULL OR st.phase = 'NEW'))""";

    public static final String BASE_FROM = """
            FROM incident i
            JOIN priority p ON p.id = i.priority_id
            JOIN status st ON st.id = i.status_id
            """;

    private IncidentSql() {
    }

    public record Fragment(String sql, Map<String, Object> params) {
    }

    public static Fragment where(IncidentCriteria c) {
        StringBuilder sb = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c == null) {
            return new Fragment(sb.toString(), params);
        }
        if (c.q() != null && !c.q().isBlank()) {
            String q = c.q().strip();
            // код целиком/префиксом, полнотекстовый поиск (морфология русского) и подстрока в названии
            sb.append(" " + """
                     AND (i.code LIKE :qPrefix
                          OR i.external_id = :q
                          OR i.search @@ websearch_to_tsquery('russian', :q)
                          OR i.title ILIKE :qLike)""");
            params.put("q", q);
            params.put("qPrefix", escapeLike(q) + "%");
            params.put("qLike", "%" + escapeLike(q) + "%");
        }
        if (c.registeredFrom() != null) {
            sb.append(" AND i.registered_at >= :registeredFrom");
            params.put("registeredFrom", c.registeredFrom());
        }
        if (c.registeredTo() != null) {
            sb.append(" AND i.registered_at <= :registeredTo");
            params.put("registeredTo", c.registeredTo());
        }
        if (IncidentCriteria.present(c.priorityIds())) {
            sb.append(" AND i.priority_id IN (:priorityIds)");
            params.put("priorityIds", c.priorityIds());
        }
        if (IncidentCriteria.present(c.statusIds())) {
            sb.append(" AND i.status_id IN (:statusIds)");
            params.put("statusIds", c.statusIds());
        }
        boolean assignees = IncidentCriteria.present(c.assigneeIds());
        boolean unassigned = Boolean.TRUE.equals(c.unassigned());
        if (assignees && unassigned) {
            sb.append(" AND (i.assignee_id IN (:assigneeIds) OR i.assignee_id IS NULL)");
            params.put("assigneeIds", c.assigneeIds());
        } else if (assignees) {
            sb.append(" AND i.assignee_id IN (:assigneeIds)");
            params.put("assigneeIds", c.assigneeIds());
        } else if (unassigned) {
            sb.append(" AND i.assignee_id IS NULL");
        }
        if (IncidentCriteria.present(c.ciIds())) {
            sb.append(" AND EXISTS (SELECT 1 FROM incident_ci f WHERE f.incident_id = i.id AND f.ci_id IN (:ciIds))");
            params.put("ciIds", c.ciIds());
        }
        if (IncidentCriteria.present(c.institutionIds())) {
            sb.append(" " + """
                     AND EXISTS (SELECT 1 FROM incident_ci f JOIN configuration_item fc ON fc.id = f.ci_id
                                 WHERE f.incident_id = i.id AND fc.institution_id IN (:institutionIds))""");
            params.put("institutionIds", c.institutionIds());
        }
        if (c.slaBreached() != null) {
            sb.append(c.slaBreached()
                    ? " AND " + RESOLUTION_BREACHED
                    : " AND " + RESOLUTION_APPLICABLE + " AND NOT " + RESOLUTION_BREACHED);
        }
        return new Fragment(sb.toString(), params);
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
