package io.github.yangorbunov.incidents.incident;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Параметры фильтрации инцидентов (экран «Категория инцидентов», Рис. 3.12).
 * Один и тот же объект используется для поиска в списке, сохраняется
 * в категории (JSONB) и применяется при построении отчёта.
 * Пустое поле = «не фильтровать».
 */
public record IncidentCriteria(
        String q,
        OffsetDateTime registeredFrom,
        OffsetDateTime registeredTo,
        List<Long> priorityIds,
        List<Long> statusIds,
        List<Long> assigneeIds,
        Boolean unassigned,
        List<Long> ciIds,
        List<Long> institutionIds,
        /** true — только с нарушением SLA решения, false — только без нарушения. */
        Boolean slaBreached
) {

    public static IncidentCriteria empty() {
        return new IncidentCriteria(null, null, null, null, null, null, null, null, null, null);
    }

    static boolean present(List<Long> list) {
        return list != null && !list.isEmpty();
    }
}
