package io.github.yangorbunov.incidents.tracker;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Порт к системе управления задачами. В ВКР интеграция — ключевое
 * требование (Табл. 1.2), но описана одной фразой: «импорт путём введения
 * идентификатора». Здесь — явный контракт и две реализации: встроенные
 * тестовые данные и REST-клиент к реальной системе.
 */
public interface TaskTrackerClient {

    Optional<ExternalIncident> findIncident(String externalId);

    Optional<ExternalCi> findCi(String externalId);

    /** Инцидент в терминах трекера: приоритет и статус — его собственные названия. */
    record ExternalIncident(
            String id,
            String title,
            String description,
            String resolution,
            OffsetDateTime createdAt,
            OffsetDateTime firstResponseAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime closedAt,
            String priority,
            String status,
            String assignee,
            List<String> configurationItems
    ) {
        public List<String> configurationItems() {
            return configurationItems == null ? List.of() : configurationItems;
        }
    }

    record ExternalCi(
            String id,
            String name,
            String type,
            String institutionCode,
            String ipAddress,
            String technicalSpecs,
            List<ExternalSoftware> software
    ) {
        public List<ExternalSoftware> software() {
            return software == null ? List.of() : software;
        }
    }

    record ExternalSoftware(String name, String version, String vendor) {
    }
}
