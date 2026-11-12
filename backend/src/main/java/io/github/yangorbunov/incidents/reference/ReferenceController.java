package io.github.yangorbunov.incidents.reference;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.reference.ReferenceDtos.*;
import io.github.yangorbunov.incidents.reference.ReferenceRepository.ReferenceKind;
import jakarta.validation.Valid;

/**
 * Справочники. Статусы и приоритеты влияют на аналитику (фазы, SLA),
 * поэтому их меняет только администратор; остальное — и аналитик.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final ReferenceService service;
    private final ReferenceRepository repo;

    public ReferenceController(ReferenceService service, ReferenceRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @GetMapping
    public Bundle all() {
        return service.bundle();
    }

    // --- отделы и должности (администратор) ---
    @GetMapping("/departments")
    public List<Department> departments() {
        return repo.departments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> createDepartment(@Valid @RequestBody DepartmentRequest r) {
        return id(service.createDepartment(r));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void updateDepartment(@PathVariable long id, @Valid @RequestBody DepartmentRequest r) {
        service.updateDepartment(id, r);
    }

    @GetMapping("/posts")
    public List<Post> posts() {
        return repo.posts();
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> createPost(@Valid @RequestBody PostRequest r) {
        return id(service.createPost(r));
    }

    @PutMapping("/posts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void updatePost(@PathVariable long id, @Valid @RequestBody PostRequest r) {
        service.updatePost(id, r);
    }

    // --- приоритеты и статусы (администратор) ---
    @GetMapping("/priorities")
    public List<Priority> priorities() {
        return repo.priorities();
    }

    @PostMapping("/priorities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> createPriority(@Valid @RequestBody PriorityRequest r) {
        return id(service.createPriority(r));
    }

    @PutMapping("/priorities/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void updatePriority(@PathVariable long id, @Valid @RequestBody PriorityRequest r) {
        service.updatePriority(id, r);
    }

    @GetMapping("/statuses")
    public List<Status> statuses() {
        return repo.statuses();
    }

    @PostMapping("/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Long> createStatus(@Valid @RequestBody StatusRequest r) {
        return id(service.createStatus(r));
    }

    @PutMapping("/statuses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void updateStatus(@PathVariable long id, @Valid @RequestBody StatusRequest r) {
        service.updateStatus(id, r);
    }

    // --- ведомства, типы КЕ, ПО, сотрудники (аналитик) ---
    @GetMapping("/institutions")
    public List<Institution> institutions() {
        return repo.institutions();
    }

    @PostMapping("/institutions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createInstitution(@Valid @RequestBody InstitutionRequest r) {
        return id(service.createInstitution(r));
    }

    @PutMapping("/institutions/{id}")
    public void updateInstitution(@PathVariable long id, @Valid @RequestBody InstitutionRequest r) {
        service.updateInstitution(id, r);
    }

    @GetMapping("/ci-types")
    public List<CiType> ciTypes() {
        return repo.ciTypes();
    }

    @PostMapping("/ci-types")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createCiType(@Valid @RequestBody CiTypeRequest r) {
        return id(service.createCiType(r));
    }

    @PutMapping("/ci-types/{id}")
    public void updateCiType(@PathVariable long id, @Valid @RequestBody CiTypeRequest r) {
        service.updateCiType(id, r);
    }

    @GetMapping("/software")
    public List<Software> software() {
        return repo.software();
    }

    @PostMapping("/software")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createSoftware(@Valid @RequestBody SoftwareRequest r) {
        return id(service.createSoftware(r));
    }

    @PutMapping("/software/{id}")
    public void updateSoftware(@PathVariable long id, @Valid @RequestBody SoftwareRequest r) {
        service.updateSoftware(id, r);
    }

    @GetMapping("/employees")
    public List<Employee> employees() {
        return repo.employees();
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createEmployee(@Valid @RequestBody EmployeeRequest r) {
        return id(service.createEmployee(r));
    }

    @PutMapping("/employees/{id}")
    public void updateEmployee(@PathVariable long id, @Valid @RequestBody EmployeeRequest r) {
        service.updateEmployee(id, r);
    }

    // --- удаление ---
    @DeleteMapping("/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String kind, @PathVariable long id) {
        ReferenceKind k = switch (kind) {
            case "departments" -> ReferenceKind.DEPARTMENT;
            case "posts" -> ReferenceKind.POST;
            case "priorities" -> ReferenceKind.PRIORITY;
            case "statuses" -> ReferenceKind.STATUS;
            case "institutions" -> ReferenceKind.INSTITUTION;
            case "ci-types" -> ReferenceKind.CI_TYPE;
            case "software" -> ReferenceKind.SOFTWARE;
            case "employees" -> ReferenceKind.EMPLOYEE;
            default -> throw new io.github.yangorbunov.incidents.common.NotFoundException("Справочник", kind);
        };
        if (k == ReferenceKind.DEPARTMENT || k == ReferenceKind.POST
                || k == ReferenceKind.PRIORITY || k == ReferenceKind.STATUS) {
            requireAdmin();
        }
        service.delete(k, id);
    }

    private static void requireAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            throw new org.springframework.security.access.AccessDeniedException("Требуются права администратора");
        }
    }

    private static Map<String, Long> id(long id) {
        return Map.of("id", id);
    }
}
