package io.github.yangorbunov.incidents.reference;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.common.CodeGenerator;
import io.github.yangorbunov.incidents.common.NotFoundException;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Bundle;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.CiTypeRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.DepartmentRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.EmployeeRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.InstitutionRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.PostRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.PriorityRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.SoftwareRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.StatusRequest;
import io.github.yangorbunov.incidents.reference.ReferenceRepository.ReferenceKind;

@Service
public class ReferenceService {

    private final ReferenceRepository repo;
    private final CodeGenerator codes;
    private final AuditService audit;

    public ReferenceService(ReferenceRepository repo, CodeGenerator codes, AuditService audit) {
        this.repo = repo;
        this.codes = codes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Bundle bundle() {
        return new Bundle(repo.departments(), repo.posts(), repo.priorities(), repo.statuses(),
                repo.institutions(), repo.ciTypes(), repo.software(), repo.employees());
    }

    @Transactional
    public long createDepartment(DepartmentRequest r) {
        return created(ReferenceKind.DEPARTMENT, repo.insertDepartment(codes.sequential("DEPARTMENT", 2), r), r.name());
    }

    @Transactional
    public void updateDepartment(long id, DepartmentRequest r) {
        updated(ReferenceKind.DEPARTMENT, id, repo.updateDepartment(id, r));
    }

    @Transactional
    public long createPost(PostRequest r) {
        var dep = repo.department(r.departmentId())
                .orElseThrow(() -> new NotFoundException("Отдел", r.departmentId()));
        return created(ReferenceKind.POST, repo.insertPost(codes.postCode(dep.code()), r), r.name());
    }

    @Transactional
    public void updatePost(long id, PostRequest r) {
        updated(ReferenceKind.POST, id, repo.updatePost(id, r));
    }

    @Transactional
    public long createPriority(PriorityRequest r) {
        return created(ReferenceKind.PRIORITY, repo.insertPriority(codes.sequential("PRIORITY", 2), r), r.name());
    }

    @Transactional
    public void updatePriority(long id, PriorityRequest r) {
        updated(ReferenceKind.PRIORITY, id, repo.updatePriority(id, r));
    }

    @Transactional
    public long createStatus(StatusRequest r) {
        return created(ReferenceKind.STATUS, repo.insertStatus(codes.sequential("STATUS", 2), r), r.name());
    }

    @Transactional
    public void updateStatus(long id, StatusRequest r) {
        updated(ReferenceKind.STATUS, id, repo.updateStatus(id, r));
    }

    @Transactional
    public long createInstitution(InstitutionRequest r) {
        return created(ReferenceKind.INSTITUTION,
                repo.insertInstitution(codes.sequential("INSTITUTION", 3), r), r.name());
    }

    @Transactional
    public void updateInstitution(long id, InstitutionRequest r) {
        updated(ReferenceKind.INSTITUTION, id, repo.updateInstitution(id, r));
    }

    @Transactional
    public long createCiType(CiTypeRequest r) {
        return created(ReferenceKind.CI_TYPE, repo.insertCiType(codes.sequential("CI_TYPE", 3), r), r.name());
    }

    @Transactional
    public void updateCiType(long id, CiTypeRequest r) {
        updated(ReferenceKind.CI_TYPE, id, repo.updateCiType(id, r));
    }

    @Transactional
    public long createSoftware(SoftwareRequest r) {
        return created(ReferenceKind.SOFTWARE, repo.insertSoftware(codes.sequential("SOFTWARE", 5), r), r.name());
    }

    @Transactional
    public void updateSoftware(long id, SoftwareRequest r) {
        updated(ReferenceKind.SOFTWARE, id, repo.updateSoftware(id, r));
    }

    @Transactional
    public long createEmployee(EmployeeRequest r) {
        var post = repo.post(r.postId()).orElseThrow(() -> new NotFoundException("Должность", r.postId()));
        long id = repo.insertEmployee(codes.employeeCode(post.code()), r);
        return created(ReferenceKind.EMPLOYEE, id, r.lastName() + " " + r.firstName());
    }

    /** Код сотрудника при смене должности не меняется: коды неизменяемы, иначе ломаются внешние ссылки. */
    @Transactional
    public void updateEmployee(long id, EmployeeRequest r) {
        updated(ReferenceKind.EMPLOYEE, id, repo.updateEmployee(id, r));
    }

    @Transactional
    public void delete(ReferenceKind kind, long id) {
        if (repo.delete(kind, id) == 0) {
            throw new NotFoundException(kind.name(), id);
        }
        audit.log("REFERENCE_DELETED", kind.name(), id, Map.of());
    }

    private long created(ReferenceKind kind, long id, String name) {
        audit.log("REFERENCE_CREATED", kind.name(), id, Map.of("name", name));
        return id;
    }

    private void updated(ReferenceKind kind, long id, int rows) {
        if (rows == 0) {
            throw new NotFoundException(kind.name(), id);
        }
        audit.log("REFERENCE_UPDATED", kind.name(), id, Map.of());
    }
}
