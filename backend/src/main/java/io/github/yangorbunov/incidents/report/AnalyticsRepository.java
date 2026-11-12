package io.github.yangorbunov.incidents.report;

import static io.github.yangorbunov.incidents.incident.IncidentSql.BASE_FROM;
import static io.github.yangorbunov.incidents.incident.IncidentSql.RESOLUTION_APPLICABLE;
import static io.github.yangorbunov.incidents.incident.IncidentSql.RESOLUTION_BREACHED;
import static io.github.yangorbunov.incidents.incident.IncidentSql.RESPONSE_APPLICABLE;
import static io.github.yangorbunov.incidents.incident.IncidentSql.RESPONSE_BREACHED;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.yangorbunov.incidents.config.AppProperties;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentListItem;
import io.github.yangorbunov.incidents.incident.IncidentRepository;
import io.github.yangorbunov.incidents.report.ReportDtos.GroupStat;
import io.github.yangorbunov.incidents.report.ReportDtos.PriorityStat;
import io.github.yangorbunov.incidents.report.ReportDtos.StatusStat;
import io.github.yangorbunov.incidents.report.ReportDtos.Summary;
import io.github.yangorbunov.incidents.report.ReportDtos.TrendPoint;

/**
 * Аналитика по срезу категории. Всё считается в PostgreSQL одним проходом
 * на показатель — в ВКР это делалось вручную в Excel.
 */
@Repository
public class AnalyticsRepository {

    /** Срез категории: присоединяется к BASE_FROM. */
    private static final String SCOPE = """
            JOIN category_incident sc ON sc.incident_id = i.id AND sc.category_id = :categoryId
            """;

    private static final String RESOLUTION_SECONDS = "extract(epoch FROM i.closed_at - i.registered_at)";

    private final JdbcClient jdbc;
    private final String zone;

    public AnalyticsRepository(JdbcClient jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.zone = properties.zone().getId();
    }

    public Summary summary(long categoryId) {
        return jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE st.phase = 'CLOSED') AS closed,
                               count(*) FILTER (WHERE st.phase IN ('RESOLVED', 'CLOSED')) AS resolved,
                               round(100.0 * count(*) FILTER (WHERE st.phase = 'CLOSED')
                                     / nullif(count(*), 0), 2)::float8 AS closed_pct,
                               (avg(%1$s) FILTER (WHERE i.closed_at IS NOT NULL))::bigint AS avg_resolution_seconds,
                               (percentile_cont(0.5) WITHIN GROUP (ORDER BY %1$s)
                                    FILTER (WHERE i.closed_at IS NOT NULL))::bigint AS median_resolution_seconds,
                               (percentile_cont(0.9) WITHIN GROUP (ORDER BY %1$s)
                                    FILTER (WHERE i.closed_at IS NOT NULL))::bigint AS p90_resolution_seconds,
                               (avg(extract(epoch FROM i.first_response_at - i.registered_at))
                                    FILTER (WHERE i.first_response_at IS NOT NULL))::bigint AS avg_response_seconds,
                               count(*) FILTER (WHERE %2$s) AS resolution_sla_total,
                               count(*) FILTER (WHERE %2$s AND %3$s) AS resolution_sla_breached,
                               count(*) FILTER (WHERE %4$s) AS response_sla_total,
                               count(*) FILTER (WHERE %4$s AND %5$s) AS response_sla_breached
                        """.formatted(RESOLUTION_SECONDS, RESOLUTION_APPLICABLE, RESOLUTION_BREACHED,
                        RESPONSE_APPLICABLE, RESPONSE_BREACHED)
                        + BASE_FROM + SCOPE)
                .param("categoryId", categoryId)
                .query(Summary.class)
                .single();
    }

    /** Все приоритеты, включая нулевые — для легенды круговой диаграммы (Рис. 2.6). */
    public List<PriorityStat> byPriority(long categoryId) {
        return jdbc.sql("""
                        SELECT p.id, p.name, p.color,
                               count(i.id) AS total,
                               count(i.id) FILTER (WHERE st.phase = 'CLOSED') AS closed,
                               (avg(%1$s) FILTER (WHERE i.closed_at IS NOT NULL))::bigint AS avg_resolution_seconds,
                               count(i.id) FILTER (WHERE %2$s) AS sla_total,
                               count(i.id) FILTER (WHERE %2$s AND %3$s) AS sla_breached
                        FROM priority p
                        LEFT JOIN (incident i
                                   JOIN status st ON st.id = i.status_id
                                   JOIN category_incident sc ON sc.incident_id = i.id AND sc.category_id = :categoryId)
                               ON i.priority_id = p.id
                        GROUP BY p.id
                        ORDER BY p.sort_order, p.code
                        """.formatted(RESOLUTION_SECONDS, RESOLUTION_APPLICABLE, RESOLUTION_BREACHED))
                .param("categoryId", categoryId)
                .query(PriorityStat.class)
                .list();
    }

    public List<StatusStat> byStatus(long categoryId) {
        return jdbc.sql("""
                        SELECT st.id, st.name, st.phase, count(i.id) AS total
                        FROM status st
                        LEFT JOIN (incident i
                                   JOIN category_incident sc ON sc.incident_id = i.id AND sc.category_id = :categoryId)
                               ON i.status_id = st.id
                        GROUP BY st.id
                        ORDER BY st.sort_order, st.code
                        """)
                .param("categoryId", categoryId)
                .query(StatusStat.class)
                .list();
    }

    /**
     * По ведомствам: инцидент с КЕ двух ведомств учитывается в обоих
     * (сумма по строкам может быть больше общего числа).
     */
    public List<GroupStat> byInstitution(long categoryId) {
        return groupBy(categoryId, """
                JOIN incident_ci ic ON ic.incident_id = i.id
                JOIN configuration_item c ON c.id = ic.ci_id
                JOIN institution g ON g.id = c.institution_id
                """, "g.id", "g.name", 50);
    }

    public List<GroupStat> topCis(long categoryId, int limit) {
        return groupBy(categoryId, """
                JOIN incident_ci ic ON ic.incident_id = i.id
                JOIN configuration_item g ON g.id = ic.ci_id
                """, "g.id", "g.name || ' (' || g.code || ')'", limit);
    }

    public List<GroupStat> byAssignee(long categoryId) {
        return groupBy(categoryId, "LEFT JOIN employee g ON g.id = i.assignee_id\n",
                "g.id", "coalesce(g.last_name || ' ' || left(g.first_name, 1) || '.', 'Не назначен')", 50);
    }

    private List<GroupStat> groupBy(long categoryId, String join, String idExpr, String nameExpr, int limit) {
        return jdbc.sql("""
                        SELECT %1$s AS id, %2$s AS name,
                               count(DISTINCT i.id) AS total,
                               count(DISTINCT i.id) FILTER (WHERE st.phase = 'CLOSED') AS closed,
                               count(DISTINCT i.id) FILTER (WHERE %3$s) AS sla_breached,
                               (avg(%4$s) FILTER (WHERE i.closed_at IS NOT NULL))::bigint AS avg_resolution_seconds
                        """.formatted(idExpr, nameExpr, RESOLUTION_BREACHED, RESOLUTION_SECONDS)
                        + BASE_FROM + SCOPE + join
                        + " GROUP BY " + idExpr + ", " + nameExpr + " ORDER BY total DESC, name LIMIT :limit")
                .param("categoryId", categoryId)
                .param("limit", limit)
                .query(GroupStat.class)
                .list();
    }

    /** Понедельная динамика: сколько зарегистрировано и сколько закрыто. */
    public List<TrendPoint> weeklyTrend(long categoryId) {
        return jdbc.sql("""
                        WITH s AS (
                            SELECT i.registered_at AT TIME ZONE :zone AS reg,
                                   i.closed_at AT TIME ZONE :zone AS clo
                            FROM incident i
                            JOIN category_incident sc ON sc.incident_id = i.id AND sc.category_id = :categoryId
                        ),
                        weeks AS (
                            SELECT generate_series(date_trunc('week', min(reg)),
                                                   date_trunc('week', greatest(max(reg), coalesce(max(clo), max(reg)))),
                                                   INTERVAL '1 week') AS wk
                            FROM s
                        )
                        SELECT w.wk::date AS week_start,
                               count(s.reg) FILTER (WHERE date_trunc('week', s.reg) = w.wk) AS registered,
                               count(s.clo) FILTER (WHERE date_trunc('week', s.clo) = w.wk) AS closed
                        FROM weeks w
                        CROSS JOIN s
                        GROUP BY w.wk
                        ORDER BY w.wk
                        """)
                .param("zone", zone)
                .param("categoryId", categoryId)
                .query(TrendPoint.class)
                .list();
    }

    public List<IncidentListItem> incidents(long categoryId) {
        return jdbc.sql(IncidentRepository.LIST_SELECT + SCOPE + " ORDER BY i.registered_at DESC, i.id DESC")
                .param("categoryId", categoryId)
                .query(IncidentListItem.class)
                .list();
    }
}
