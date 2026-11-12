package io.github.yangorbunov.incidents.report;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import io.github.yangorbunov.incidents.category.CategoryService.Category;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentListItem;

public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * Показатели категории. В ВКР их два: процент закрытых (2.1) и среднее
     * время разрешения (2.2). Добавлены медиана и 90-й перцентиль (среднее
     * по длинному хвосту врёт), время реакции и соблюдение SLA.
     */
    public record Summary(
            long total,
            long closed,
            long resolved,
            /** Формула 2.1: P = C / T × 100%. */
            Double closedPct,
            /** Формула 2.2 — по закрытым инцидентам (у открытых нет T закрытия). */
            Long avgResolutionSeconds,
            Long medianResolutionSeconds,
            Long p90ResolutionSeconds,
            Long avgResponseSeconds,
            long resolutionSlaTotal,
            long resolutionSlaBreached,
            long responseSlaTotal,
            long responseSlaBreached
    ) {
        public Double resolutionSlaCompliancePct() {
            return pct(resolutionSlaTotal - resolutionSlaBreached, resolutionSlaTotal);
        }

        public Double responseSlaCompliancePct() {
            return pct(responseSlaTotal - responseSlaBreached, responseSlaTotal);
        }

        private static Double pct(long part, long total) {
            return total == 0 ? null : Math.round(10000.0 * part / total) / 100.0;
        }
    }

    public record PriorityStat(long id, String name, String color, long total, long closed,
                               Long avgResolutionSeconds, long slaTotal, long slaBreached) {
    }

    public record StatusStat(long id, String name, String phase, long total) {
    }

    /** Разрез по ведомству / КЕ / ответственному. */
    public record GroupStat(Long id, String name, long total, long closed, long slaBreached,
                            Long avgResolutionSeconds) {
    }

    public record TrendPoint(LocalDate weekStart, long registered, long closed) {
    }

    public record Author(String username, String name, String post) {
    }

    public record Report(
            Category category,
            OffsetDateTime generatedAt,
            Author author,
            Summary summary,
            List<PriorityStat> byPriority,
            List<StatusStat> byStatus,
            List<GroupStat> byInstitution,
            List<GroupStat> topCis,
            List<GroupStat> byAssignee,
            List<TrendPoint> trend,
            List<IncidentListItem> incidents
    ) {
    }
}
