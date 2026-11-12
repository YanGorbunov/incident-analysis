package io.github.yangorbunov.incidents.incident;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.common.CodeGenerator;
import io.github.yangorbunov.incidents.common.ConflictException;
import io.github.yangorbunov.incidents.common.NotFoundException;
import io.github.yangorbunov.incidents.common.ValidationException;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentDetails;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentRequest;
import io.github.yangorbunov.incidents.incident.IncidentDtos.Source;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Phase;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Status;
import io.github.yangorbunov.incidents.reference.ReferenceRepository;

/**
 * Жизненный цикл инцидента. Главное правило: моменты реакции, решения и
 * закрытия выводятся из смены статуса (по фазе), а не вводятся руками —
 * иначе аналитика SLA держится на дисциплине заполнения.
 */
@Service
public class IncidentService {

    /** Допуск на рассинхронизацию часов клиента: «регистрация в будущем» на минуту — не ошибка. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);

    private final IncidentRepository repo;
    private final ReferenceRepository reference;
    private final CodeGenerator codes;
    private final AuditService audit;
    private final Clock clock;

    public IncidentService(IncidentRepository repo, ReferenceRepository reference, CodeGenerator codes,
                           AuditService audit, Clock clock) {
        this.repo = repo;
        this.reference = reference;
        this.codes = codes;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IncidentDetails get(long id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("Инцидент", id));
    }

    @Transactional
    public long create(IncidentRequest r, Source source) {
        return create(r, source, OffsetDateTime.now(clock));
    }

    /** @param changedAt момент события (для импорта — время из трекера) */
    @Transactional
    public long create(IncidentRequest r, Source source, OffsetDateTime changedAt) {
        Status status = status(r.statusId());
        var times = Timestamps.of(r).applyPhase(status.phase(), r.registeredAt(), changedAt, true);
        validate(r, times);
        String user = currentUser();
        long id = repo.insert(new IncidentRepository.Row(codes.incidentCode(r.registeredAt()), blank(r.externalId()),
                source, r.title().strip(), r.description(), blank(r.resolution()), r.registeredAt(),
                times.firstResponseAt, times.resolvedAt, times.closedAt,
                r.priorityId(), r.statusId(), r.assigneeId(), user));
        repo.replaceCis(id, r.ciIds());
        repo.addHistory(id, null, r.statusId(), r.registeredAt(), user);
        audit.log("INCIDENT_CREATED", "INCIDENT", id, Map.of("source", source, "title", r.title()));
        return id;
    }

    @Transactional
    public void update(long id, IncidentRequest r) {
        update(id, r, OffsetDateTime.now(clock), false);
    }

    /**
     * @param ignoreVersion импорт перезаписывает данные трекера безусловно —
     *                      источник истины там, а не в ручных правках
     */
    @Transactional
    public void update(long id, IncidentRequest r, OffsetDateTime changedAt, boolean ignoreVersion) {
        IncidentDetails current = get(id);
        if (!ignoreVersion && r.version() == null) {
            throw new ValidationException("Не передана версия записи");
        }
        Status status = status(r.statusId());
        boolean statusChanged = current.statusId() != r.statusId();

        // Явно заданные моменты имеют приоритет; иначе берём сохранённые и пересчитываем по фазе.
        var times = new Timestamps(
                coalesce(r.firstResponseAt(), current.firstResponseAt()),
                coalesce(r.resolvedAt(), current.resolvedAt()),
                coalesce(r.closedAt(), current.closedAt()))
                .applyPhase(status.phase(), r.registeredAt(), changedAt, statusChanged);
        validate(r, times);

        String user = currentUser();
        int version = ignoreVersion ? current.version() : r.version();
        int rows = repo.update(id, version, new IncidentRepository.Row(current.code(), blank(r.externalId()),
                current.source(), r.title().strip(), r.description(), blank(r.resolution()), r.registeredAt(),
                times.firstResponseAt, times.resolvedAt, times.closedAt,
                r.priorityId(), r.statusId(), r.assigneeId(), user));
        if (rows == 0) {
            throw ConflictException.staleVersion("Инцидент");
        }
        repo.replaceCis(id, r.ciIds());
        if (statusChanged) {
            repo.addHistory(id, current.statusId(), r.statusId(), changedAt, user);
        }
        audit.log("INCIDENT_UPDATED", "INCIDENT", id,
                statusChanged ? Map.of("fromStatus", current.statusId(), "toStatus", r.statusId()) : Map.of());
    }

    @Transactional
    public void delete(long id) {
        var current = get(id);
        repo.delete(id);
        audit.log("INCIDENT_DELETED", "INCIDENT", id, Map.of("code", current.code(), "title", current.title()));
    }

    private void validate(IncidentRequest r, Timestamps t) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (r.registeredAt().isAfter(now.plus(CLOCK_SKEW))) {
            throw new ValidationException("Дата регистрации не может быть в будущем");
        }
        if (t.closedAt != null && t.closedAt.isBefore(r.registeredAt())) {
            throw new ValidationException("Дата закрытия раньше даты регистрации");
        }
        if (t.resolvedAt != null && t.resolvedAt.isBefore(r.registeredAt())) {
            throw new ValidationException("Дата решения раньше даты регистрации");
        }
        if (t.firstResponseAt != null && t.firstResponseAt.isBefore(r.registeredAt())) {
            throw new ValidationException("Дата реакции раньше даты регистрации");
        }
        if (r.priorityId() != null && reference.priority(r.priorityId()).isEmpty()) {
            throw new ValidationException("Приоритет не найден");
        }
    }

    private Status status(long statusId) {
        return reference.status(statusId).orElseThrow(() -> new ValidationException("Статус не найден"));
    }

    static String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static <T> T coalesce(T a, T b) {
        return a != null ? a : b;
    }

    /**
     * Моменты реакции/решения/закрытия. Правила по фазе нового статуса:
     * <ul>
     *   <li>NEW — реакции, решения и закрытия ещё не было;</li>
     *   <li>ACTIVE — есть реакция; решения и закрытия нет;</li>
     *   <li>RESOLVED — есть реакция и решение; закрытия нет;</li>
     *   <li>CLOSED — есть всё.</li>
     * </ul>
     * Недостающий момент ставится в {@code changedAt}, но только при реальной
     * смене статуса — иначе правка описания «закрыла» бы инцидент сегодняшним числом.
     */
    record Timestamps(OffsetDateTime firstResponseAt, OffsetDateTime resolvedAt, OffsetDateTime closedAt) {

        static Timestamps of(IncidentRequest r) {
            return new Timestamps(r.firstResponseAt(), r.resolvedAt(), r.closedAt());
        }

        Timestamps applyPhase(Phase phase, OffsetDateTime registeredAt, OffsetDateTime changedAt,
                              boolean fillMissing) {
            OffsetDateTime at = changedAt.isBefore(registeredAt) ? registeredAt : changedAt;
            OffsetDateTime response = firstResponseAt;
            OffsetDateTime resolved = resolvedAt;
            OffsetDateTime closed = closedAt;
            switch (phase) {
                case NEW -> {
                    response = null;
                    resolved = null;
                    closed = null;
                }
                case ACTIVE -> {
                    if (response == null && fillMissing) {
                        response = at;
                    }
                    resolved = null;
                    closed = null;
                }
                case RESOLVED -> {
                    if (resolved == null && fillMissing) {
                        resolved = at;
                    }
                    closed = null;
                }
                case CLOSED -> {
                    if (closed == null) {
                        closed = at;
                    }
                    if (resolved == null && fillMissing) {
                        resolved = closed;
                    }
                }
            }
            return new Timestamps(response, resolved, closed);
        }
    }
}
