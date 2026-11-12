package io.github.yangorbunov.incidents.reference;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.yangorbunov.incidents.common.SqlParams;

import io.github.yangorbunov.incidents.reference.ReferenceDtos.CiType;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.CiTypeRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Department;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.DepartmentRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Employee;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.EmployeeRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Institution;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.InstitutionRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Post;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.PostRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Priority;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.PriorityRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Software;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.SoftwareRequest;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Status;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.StatusRequest;

@Repository
public class ReferenceRepository {

    private static final String EMPLOYEE_SELECT = """
            SELECT e.id, e.code, e.external_id, e.last_name, e.first_name, e.middle_name, e.email,
                   e.post_id, p.name AS post_name, d.id AS department_id, d.name AS department_name, e.active,
                   e.last_name || ' ' || left(e.first_name, 1) || '.'
                       || coalesce(' ' || left(e.middle_name, 1) || '.', '') AS short_name
            FROM employee e
            JOIN post p ON p.id = e.post_id
            JOIN department d ON d.id = p.department_id
            """;

    private final JdbcClient jdbc;

    public ReferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ Отделы
    public List<Department> departments() {
        return jdbc.sql("SELECT * FROM department ORDER BY code").query(Department.class).list();
    }

    public Optional<Department> department(long id) {
        return jdbc.sql("SELECT * FROM department WHERE id = :id").param("id", id).query(Department.class).optional();
    }

    public long insertDepartment(String code, DepartmentRequest r) {
        return jdbc.sql("""
                        INSERT INTO department (code, name, description, location)
                        VALUES (:code, :name, :description, :location) RETURNING id""")
                .paramSource(SqlParams.of(r).addValue("code", code)).query(Long.class).single();
    }

    public int updateDepartment(long id, DepartmentRequest r) {
        return jdbc.sql("""
                        UPDATE department SET name = :name, description = :description, location = :location
                        WHERE id = :id""")
                .paramSource(SqlParams.of(r).addValue("id", id)).update();
    }

    // ------------------------------------------------------------------ Должности
    public List<Post> posts() {
        return jdbc.sql("""
                        SELECT p.id, p.code, p.department_id, d.name AS department_name, p.name, p.description
                        FROM post p JOIN department d ON d.id = p.department_id ORDER BY p.code""")
                .query(Post.class).list();
    }

    public Optional<Post> post(long id) {
        return jdbc.sql("""
                        SELECT p.id, p.code, p.department_id, d.name AS department_name, p.name, p.description
                        FROM post p JOIN department d ON d.id = p.department_id WHERE p.id = :id""")
                .param("id", id).query(Post.class).optional();
    }

    public long insertPost(String code, PostRequest r) {
        return jdbc.sql("""
                        INSERT INTO post (code, department_id, name, description)
                        VALUES (:code, :departmentId, :name, :description) RETURNING id""")
                .paramSource(SqlParams.of(r).addValue("code", code)).query(Long.class).single();
    }

    /** Отдел должности не меняется: он зашит в её код ОО.XX. */
    public int updatePost(long id, PostRequest r) {
        return jdbc.sql("UPDATE post SET name = :name, description = :description WHERE id = :id")
                .paramSource(SqlParams.of(r).addValue("id", id)).update();
    }

    // ------------------------------------------------------------------ Приоритеты
    public List<Priority> priorities() {
        return jdbc.sql("SELECT * FROM priority ORDER BY sort_order, code").query(Priority.class).list();
    }

    public Optional<Priority> priority(long id) {
        return jdbc.sql("SELECT * FROM priority WHERE id = :id").param("id", id).query(Priority.class).optional();
    }

    public Optional<Priority> priorityByCode(String code) {
        return jdbc.sql("SELECT * FROM priority WHERE code = :code").param("code", code)
                .query(Priority.class).optional();
    }

    public long insertPriority(String code, PriorityRequest r) {
        return jdbc.sql("""
                        INSERT INTO priority (code, name, description, sort_order, color,
                                              response_sla_minutes, resolution_sla_minutes)
                        VALUES (:code, :name, :description, :sortOrder, :color,
                                :responseSlaMinutes, :resolutionSlaMinutes) RETURNING id""")
                .paramSource(SqlParams.of(r).addValue("code", code)).query(Long.class).single();
    }

    public int updatePriority(long id, PriorityRequest r) {
        return jdbc.sql("""
                        UPDATE priority SET name = :name, description = :description, sort_order = :sortOrder,
                               color = :color, response_sla_minutes = :responseSlaMinutes,
                               resolution_sla_minutes = :resolutionSlaMinutes
                        WHERE id = :id""")
                .paramSource(SqlParams.of(r).addValue("id", id)).update();
    }

    // ------------------------------------------------------------------ Статусы
    public List<Status> statuses() {
        return jdbc.sql("SELECT * FROM status ORDER BY sort_order, code").query(Status.class).list();
    }

    public Optional<Status> status(long id) {
        return jdbc.sql("SELECT * FROM status WHERE id = :id").param("id", id).query(Status.class).optional();
    }

    public Optional<Status> statusByCode(String code) {
        return jdbc.sql("SELECT * FROM status WHERE code = :code").param("code", code).query(Status.class).optional();
    }

    public long insertStatus(String code, StatusRequest r) {
        return jdbc.sql("""
                        INSERT INTO status (code, name, description, sort_order, phase)
                        VALUES (:code, :name, :description, :sortOrder, :phase) RETURNING id""")
                .param("code", code)
                .param("name", r.name()).param("description", r.description())
                .param("sortOrder", r.sortOrder()).param("phase", r.phase().name())
                .query(Long.class).single();
    }

    public int updateStatus(long id, StatusRequest r) {
        return jdbc.sql("""
                        UPDATE status SET name = :name, description = :description, sort_order = :sortOrder,
                               phase = :phase
                        WHERE id = :id""")
                .param("name", r.name()).param("description", r.description())
                .param("sortOrder", r.sortOrder()).param("phase", r.phase().name())
                .param("id", id).update();
    }

    // ------------------------------------------------------------------ Ведомства
    public List<Institution> institutions() {
        return jdbc.sql("SELECT * FROM institution ORDER BY code").query(Institution.class).list();
    }

    public Optional<Institution> institution(long id) {
        return jdbc.sql("SELECT * FROM institution WHERE id = :id").param("id", id)
                .query(Institution.class).optional();
    }

    public Optional<Institution> institutionByCode(String code) {
        return jdbc.sql("SELECT * FROM institution WHERE code = :code").param("code", code)
                .query(Institution.class).optional();
    }

    public long insertInstitution(String code, InstitutionRequest r) {
        return jdbc.sql("""
                        INSERT INTO institution (code, name, phone_number, address)
                        VALUES (:code, :name, :phoneNumber, :address) RETURNING id""")
                .paramSource(SqlParams.of(r).addValue("code", code)).query(Long.class).single();
    }

    public int updateInstitution(long id, InstitutionRequest r) {
        return jdbc.sql("""
                        UPDATE institution SET name = :name, phone_number = :phoneNumber, address = :address
                        WHERE id = :id""")
                .paramSource(SqlParams.of(r).addValue("id", id)).update();
    }

    // ------------------------------------------------------------------ Типы КЕ
    public List<CiType> ciTypes() {
        return jdbc.sql("SELECT * FROM ci_type ORDER BY code").query(CiType.class).list();
    }

    public Optional<CiType> ciTypeByName(String name) {
        return jdbc.sql("SELECT * FROM ci_type WHERE lower(name) = lower(:name)").param("name", name)
                .query(CiType.class).optional();
    }

    public long insertCiType(String code, CiTypeRequest r) {
        return jdbc.sql("INSERT INTO ci_type (code, name) VALUES (:code, :name) RETURNING id")
                .param("code", code).param("name", r.name()).query(Long.class).single();
    }

    public int updateCiType(long id, CiTypeRequest r) {
        return jdbc.sql("UPDATE ci_type SET name = :name WHERE id = :id")
                .param("name", r.name()).param("id", id).update();
    }

    // ------------------------------------------------------------------ ПО
    public List<Software> software() {
        return jdbc.sql("SELECT * FROM software ORDER BY name, version").query(Software.class).list();
    }

    public Optional<Software> softwareByNameVersion(String name, String version) {
        return jdbc.sql("""
                        SELECT * FROM software
                        WHERE lower(name) = lower(:name) AND version IS NOT DISTINCT FROM CAST(:version AS varchar)""")
                .param("name", name).param("version", version).query(Software.class).optional();
    }

    public long insertSoftware(String code, SoftwareRequest r) {
        return jdbc.sql("""
                        INSERT INTO software (code, name, version, vendor, license_key)
                        VALUES (:code, :name, :version, :vendor, :licenseKey) RETURNING id""")
                .paramSource(SqlParams.of(r).addValue("code", code)).query(Long.class).single();
    }

    public int updateSoftware(long id, SoftwareRequest r) {
        return jdbc.sql("""
                        UPDATE software SET name = :name, version = :version, vendor = :vendor,
                               license_key = :licenseKey
                        WHERE id = :id""")
                .paramSource(SqlParams.of(r).addValue("id", id)).update();
    }

    // ------------------------------------------------------------------ Сотрудники
    public List<Employee> employees() {
        return jdbc.sql(EMPLOYEE_SELECT + " ORDER BY e.last_name, e.first_name").query(Employee.class).list();
    }

    public Optional<Employee> employee(long id) {
        return jdbc.sql(EMPLOYEE_SELECT + " WHERE e.id = :id").param("id", id).query(Employee.class).optional();
    }

    public Optional<Employee> employeeByExternalId(String externalId) {
        return jdbc.sql(EMPLOYEE_SELECT + " WHERE e.external_id = :ext").param("ext", externalId)
                .query(Employee.class).optional();
    }

    public long insertEmployee(String code, EmployeeRequest r) {
        return jdbc.sql("""
                        INSERT INTO employee (code, external_id, last_name, first_name, middle_name, email, post_id, active)
                        VALUES (:code, :externalId, :lastName, :firstName, :middleName, :email, :postId, :active)
                        RETURNING id""")
                .paramSource(SqlParams.of(r).addValue("code", code)).query(Long.class).single();
    }

    public int updateEmployee(long id, EmployeeRequest r) {
        return jdbc.sql("""
                        UPDATE employee SET external_id = :externalId, last_name = :lastName, first_name = :firstName,
                               middle_name = :middleName, email = :email, post_id = :postId, active = :active
                        WHERE id = :id""")
                .paramSource(SqlParams.of(r).addValue("id", id)).update();
    }

    // ------------------------------------------------------------------ Удаление
    /** Таблица приходит только из enum, не от пользователя. */
    public int delete(ReferenceKind kind, long id) {
        return jdbc.sql("DELETE FROM " + kind.table + " WHERE id = :id").param("id", id).update();
    }

    public enum ReferenceKind {
        DEPARTMENT("department"), POST("post"), PRIORITY("priority"), STATUS("status"),
        INSTITUTION("institution"), CI_TYPE("ci_type"), SOFTWARE("software"), EMPLOYEE("employee");

        final String table;

        ReferenceKind(String table) {
            this.table = table;
        }
    }
}
