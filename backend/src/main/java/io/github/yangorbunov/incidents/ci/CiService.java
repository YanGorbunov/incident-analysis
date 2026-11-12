package io.github.yangorbunov.incidents.ci;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.ci.CiDtos.CiDetails;
import io.github.yangorbunov.incidents.ci.CiDtos.CiRequest;
import io.github.yangorbunov.incidents.common.CodeGenerator;
import io.github.yangorbunov.incidents.common.ConflictException;
import io.github.yangorbunov.incidents.common.NotFoundException;
import io.github.yangorbunov.incidents.common.ValidationException;
import io.github.yangorbunov.incidents.reference.ReferenceRepository;

@Service
public class CiService {

    private final CiRepository repo;
    private final ReferenceRepository reference;
    private final CodeGenerator codes;
    private final AuditService audit;

    public CiService(CiRepository repo, ReferenceRepository reference, CodeGenerator codes, AuditService audit) {
        this.repo = repo;
        this.reference = reference;
        this.codes = codes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CiDetails get(long id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("Конфигурационная единица", id));
    }

    @Transactional
    public long create(CiRequest r) {
        var institution = reference.institution(r.institutionId())
                .orElseThrow(() -> new ValidationException("Ведомство не найдено"));
        long id = repo.insert(codes.ciCode(institution.code()), r);
        repo.replaceSoftware(id, r.softwareIds());
        audit.log("CI_CREATED", "CI", id, Map.of("name", r.name()));
        return id;
    }

    @Transactional
    public void update(long id, CiRequest r) {
        if (r.version() == null) {
            throw new ValidationException("Не передана версия записи");
        }
        if (repo.update(id, r.version(), r) == 0) {
            if (repo.find(id).isEmpty()) {
                throw new NotFoundException("Конфигурационная единица", id);
            }
            throw ConflictException.staleVersion("Конфигурационная единица");
        }
        repo.replaceSoftware(id, r.softwareIds());
        audit.log("CI_UPDATED", "CI", id, Map.of("name", r.name()));
    }

    @Transactional
    public void delete(long id) {
        if (repo.usedByIncidents(id)) {
            throw new ConflictException("КЕ связана с инцидентами — удаление исказит историю и отчёты");
        }
        if (repo.delete(id) == 0) {
            throw new NotFoundException("Конфигурационная единица", id);
        }
        audit.log("CI_DELETED", "CI", id, Map.of());
    }
}
