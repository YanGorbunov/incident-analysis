package io.github.yangorbunov.incidents.incident;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public enum Source { MANUAL, IMPORT }

    public record IncidentListItem(
            long id,
            String code,
            String externalId,
            String title,
            OffsetDateTime registeredAt,
            OffsetDateTime closedAt,
            long priorityId,
            String priorityName,
            String priorityColor,
            long statusId,
            String statusName,
            String statusPhase,
            Long assigneeId,
            String assigneeName,
            String cis,
            String institutions,
            boolean slaBreached,
            Long resolutionSeconds
    ) {
    }

    public record CiRef(long id, String code, String name, String institutionName) {
    }

    public record HistoryEntry(OffsetDateTime changedAt, String changedBy, String fromStatus, String toStatus) {
    }

    public record IncidentDetails(
            long id,
            String code,
            String externalId,
            Source source,
            String title,
            String description,
            String resolution,
            OffsetDateTime registeredAt,
            OffsetDateTime firstResponseAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime closedAt,
            long priorityId,
            long statusId,
            Long assigneeId,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            int version,
            List<CiRef> cis,
            List<HistoryEntry> history
    ) {
    }

    /**
     * Сохранение инцидента. firstResponseAt/resolvedAt/closedAt можно не
     * передавать: при смене статуса они проставятся автоматически.
     */
    public record IncidentRequest(
            @NotBlank @Size(max = 500) String title,
            @NotBlank String description,
            String resolution,
            @NotNull OffsetDateTime registeredAt,
            OffsetDateTime firstResponseAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime closedAt,
            @NotNull Long priorityId,
            @NotNull Long statusId,
            Long assigneeId,
            List<Long> ciIds,
            @Size(max = 64) String externalId,
            Integer version
    ) {
        public List<Long> ciIds() {
            return ciIds == null ? List.of() : ciIds;
        }
    }

    public record SearchRequest(
            @Valid IncidentCriteria criteria,
            String sort,
            boolean desc,
            @Min(0) int page,
            @Min(1) @Max(500) int size
    ) {
        public IncidentCriteria criteria() {
            return criteria == null ? IncidentCriteria.empty() : criteria;
        }
    }
}
