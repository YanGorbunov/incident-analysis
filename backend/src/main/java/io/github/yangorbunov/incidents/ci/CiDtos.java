package io.github.yangorbunov.incidents.ci;

import java.time.OffsetDateTime;
import java.util.List;

import io.github.yangorbunov.incidents.reference.ReferenceDtos.Software;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CiDtos {

    private CiDtos() {
    }

    public record CiListItem(
            long id,
            String code,
            String name,
            long ciTypeId,
            String ciTypeName,
            long institutionId,
            String institutionName,
            String ipAddress,
            long incidentCount
    ) {
    }

    public record CiDetails(
            long id,
            String code,
            String externalId,
            String name,
            long ciTypeId,
            String ciTypeName,
            long institutionId,
            String institutionName,
            String ipAddress,
            String technicalSpecs,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int version,
            List<Software> software
    ) {
        CiDetails withSoftware(List<Software> list) {
            return new CiDetails(id, code, externalId, name, ciTypeId, ciTypeName, institutionId, institutionName,
                    ipAddress, technicalSpecs, createdAt, updatedAt, version, list);
        }
    }

    /** В ВКР IP хранился как VARCHAR(20) — без проверки и без IPv6. Здесь тип INET. */
    public record CiRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull Long ciTypeId,
            @NotNull Long institutionId,
            @Pattern(regexp = "^[0-9a-fA-F:.]{2,45}$", message = "некорректный IP-адрес") String ipAddress,
            String technicalSpecs,
            List<Long> softwareIds,
            @Size(max = 64) String externalId,
            Integer version
    ) {
        public List<Long> softwareIds() {
            return softwareIds == null ? List.of() : softwareIds;
        }
    }
}
