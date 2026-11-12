package io.github.yangorbunov.incidents.tracker;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.ci.CiDtos.CiRequest;
import io.github.yangorbunov.incidents.ci.CiRepository;
import io.github.yangorbunov.incidents.ci.CiService;
import io.github.yangorbunov.incidents.common.ValidationException;
import io.github.yangorbunov.incidents.config.AppProperties;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentRequest;
import io.github.yangorbunov.incidents.incident.IncidentDtos.Source;
import io.github.yangorbunov.incidents.incident.IncidentRepository;
import io.github.yangorbunov.incidents.incident.IncidentService;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.CiTypeRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.SoftwareRequest;
import io.github.yangorbunov.incidents.reference.ReferenceRepository;
import io.github.yangorbunov.incidents.reference.ReferenceService;
import io.github.yangorbunov.incidents.tracker.ImportLog.Outcome;
import io.github.yangorbunov.incidents.tracker.TaskTrackerClient.ExternalCi;
import io.github.yangorbunov.incidents.tracker.TaskTrackerClient.ExternalIncident;

/**
 * Импорт по идентификатору (Рис. 2.3, «to-be»). Идемпотентен: повторный
 * импорт того же ID обновляет запись, а не плодит дубликаты — ключ
 * external_id уникален.
 */
@Service
public class ImportService {

    public record ImportResult(Outcome outcome, Long entityId, String code, List<String> warnings) {
    }

    private final TaskTrackerClient tracker;
    private final IncidentService incidents;
    private final IncidentRepository incidentRepo;
    private final CiService cis;
    private final CiRepository ciRepo;
    private final ReferenceRepository reference;
    private final ReferenceService referenceService;
    private final AppProperties.Tracker props;
    private final Clock clock;

    public ImportService(TaskTrackerClient tracker, IncidentService incidents, IncidentRepository incidentRepo,
                         CiService cis, CiRepository ciRepo, ReferenceRepository reference,
                         ReferenceService referenceService, AppProperties properties, Clock clock) {
        this.tracker = tracker;
        this.incidents = incidents;
        this.incidentRepo = incidentRepo;
        this.cis = cis;
        this.ciRepo = ciRepo;
        this.reference = reference;
        this.referenceService = referenceService;
        this.props = properties.tracker();
        this.clock = clock;
    }

    @Transactional
    public ImportResult importIncident(String externalId) {
        String id = externalId.strip();
        var ext = tracker.findIncident(id).orElse(null);
        if (ext == null) {
            return new ImportResult(Outcome.NOT_FOUND, null, null, List.of());
        }
        List<String> warnings = new ArrayList<>();

        long priorityId = reference.priorityByCode(mapped(props.priorityMapping(), ext.priority(), "приоритет"))
                .orElseThrow(() -> new ValidationException("Приоритет для «" + ext.priority() + "» не найден"))
                .id();
        long statusId = reference.statusByCode(mapped(props.statusMapping(), ext.status(), "статус"))
                .orElseThrow(() -> new ValidationException("Статус для «" + ext.status() + "» не найден"))
                .id();

        Long assigneeId = null;
        if (ext.assignee() != null && !ext.assignee().isBlank()) {
            assigneeId = reference.employeeByExternalId(ext.assignee()).map(e -> e.id()).orElse(null);
            if (assigneeId == null) {
                warnings.add("Ответственный «" + ext.assignee() + "» не найден в справочнике сотрудников");
            }
        }

        List<Long> ciIds = new ArrayList<>();
        for (String ciExt : ext.configurationItems()) {
            var ciId = ciRepo.findIdByExternalId(ciExt).or(() -> importCiInternal(ciExt, warnings));
            ciId.ifPresentOrElse(ciIds::add, () -> warnings.add("КЕ «" + ciExt + "» не найдена в трекере"));
        }

        var existing = incidentRepo.findIdByExternalId(id);
        var request = toRequest(ext, priorityId, statusId, assigneeId, ciIds);
        OffsetDateTime now = OffsetDateTime.now(clock);
        long incidentId;
        Outcome outcome;
        if (existing.isPresent()) {
            incidentId = existing.get();
            incidents.update(incidentId, request, now, true);
            outcome = Outcome.UPDATED;
        } else {
            incidentId = incidents.create(request, Source.IMPORT, now);
            outcome = Outcome.CREATED;
        }
        String code = incidentRepo.find(incidentId).map(d -> d.code()).orElse(null);
        return new ImportResult(outcome, incidentId, code, warnings);
    }

    @Transactional
    public ImportResult importCi(String externalId) {
        String id = externalId.strip();
        List<String> warnings = new ArrayList<>();
        var existing = ciRepo.findIdByExternalId(id);
        var ext = tracker.findCi(id).orElse(null);
        if (ext == null) {
            return new ImportResult(Outcome.NOT_FOUND, null, null, warnings);
        }
        long ciId;
        Outcome outcome;
        if (existing.isPresent()) {
            ciId = existing.get();
            var current = cis.get(ciId);
            cis.update(ciId, toCiRequest(ext, current.version()));
            outcome = Outcome.UPDATED;
        } else {
            ciId = cis.create(toCiRequest(ext, null));
            outcome = Outcome.CREATED;
        }
        return new ImportResult(outcome, ciId, cis.get(ciId).code(), warnings);
    }

    private Optional<Long> importCiInternal(String externalId, List<String> warnings) {
        var ext = tracker.findCi(externalId);
        if (ext.isEmpty()) {
            return Optional.empty();
        }
        warnings.add("КЕ «" + externalId + "» импортирована вместе с инцидентом");
        return Optional.of(cis.create(toCiRequest(ext.get(), null)));
    }

    private CiRequest toCiRequest(ExternalCi ext, Integer version) {
        var institution = reference.institutionByCode(ext.institutionCode())
                .orElseThrow(() -> new ValidationException(
                        "Ведомство с кодом «" + ext.institutionCode() + "» не найдено — добавьте его в справочник"));
        long typeId = reference.ciTypeByName(ext.type())
                .map(t -> t.id())
                .orElseGet(() -> referenceService.createCiType(new CiTypeRequest(ext.type())));
        List<Long> softwareIds = ext.software().stream()
                .map(s -> reference.softwareByNameVersion(s.name(), s.version())
                        .map(x -> x.id())
                        .orElseGet(() -> referenceService.createSoftware(
                                new SoftwareRequest(s.name(), s.version(), s.vendor(), null))))
                .toList();
        return new CiRequest(ext.name(), typeId, institution.id(), ext.ipAddress(), ext.technicalSpecs(),
                softwareIds, ext.id(), version);
    }

    private static IncidentRequest toRequest(ExternalIncident e, long priorityId, long statusId, Long assigneeId,
                                             List<Long> ciIds) {
        if (e.createdAt() == null) {
            throw new ValidationException("В трекере не указана дата создания инцидента");
        }
        return new IncidentRequest(
                e.title() == null || e.title().isBlank() ? "Без названия (" + e.id() + ")" : e.title(),
                e.description() == null || e.description().isBlank() ? "—" : e.description(),
                e.resolution(),
                e.createdAt(),
                e.firstResponseAt(),
                e.resolvedAt(),
                e.closedAt(),
                priorityId,
                statusId,
                assigneeId,
                ciIds,
                e.id(),
                null);
    }

    private static String mapped(Map<String, String> mapping, String external, String what) {
        if (external == null) {
            throw new ValidationException("В трекере не указан " + what);
        }
        String code = mapping.get(external);
        if (code == null) {
            throw new ValidationException("Нет соответствия для значения «" + external + "» (" + what
                    + "). Настройте app.tracker." + (what.equals("статус") ? "status" : "priority") + "-mapping");
        }
        return code;
    }
}
