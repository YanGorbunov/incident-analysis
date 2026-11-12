package io.github.yangorbunov.incidents.category;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.common.CodeGenerator;
import io.github.yangorbunov.incidents.common.NotFoundException;
import io.github.yangorbunov.incidents.incident.IncidentCriteria;
import io.github.yangorbunov.incidents.incident.IncidentSql;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.json.JsonMapper;

/**
 * Категория = сохранённый фильтр + зафиксированный по нему срез инцидентов.
 * <p>
 * Срез нужен для воспроизводимости: отчёт по категории «декабрь–февраль»,
 * отправленный руководителю, не должен поменяться оттого, что через неделю
 * кто-то переоткрыл инцидент или импортировал новый. Пересчитать срез —
 * явное действие аналитика («Обновить»).
 */
@Service
public class CategoryService {

    public record Category(
            long id,
            String code,
            String name,
            String description,
            IncidentCriteria criteria,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime snapshotAt,
            long incidentCount
    ) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            @NotNull IncidentCriteria criteria
    ) {
    }

    private record Row(long id, String code, String name, String description, String criteria,
                       OffsetDateTime createdAt, String createdBy, OffsetDateTime snapshotAt, long incidentCount) {
    }

    private static final String SELECT = """
            SELECT c.id, c.code, c.name, c.description, c.criteria::text AS criteria, c.created_at, c.created_by,
                   c.snapshot_at, (SELECT count(*) FROM category_incident ci WHERE ci.category_id = c.id) AS incident_count
            FROM category c
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final CodeGenerator codes;
    private final AuditService audit;

    public CategoryService(JdbcClient jdbc, JsonMapper json, CodeGenerator codes, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.codes = codes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return jdbc.sql(SELECT + " ORDER BY c.created_at DESC").query(Row.class).list()
                .stream().map(this::toCategory).toList();
    }

    @Transactional(readOnly = true)
    public Category get(long id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id").param("id", id).query(Row.class).optional()
                .map(this::toCategory)
                .orElseThrow(() -> new NotFoundException("Категория", id));
    }

    @Transactional
    public long create(CategoryRequest r) {
        long id = jdbc.sql("""
                        INSERT INTO category (code, name, description, criteria, created_by)
                        VALUES (:code, :name, :description, CAST(:criteria AS jsonb), :user) RETURNING id""")
                .param("code", codes.sequential("CATEGORY", 3))
                .param("name", r.name().strip())
                .param("description", r.description())
                .param("criteria", json.writeValueAsString(r.criteria()))
                .param("user", currentUser())
                .query(Long.class)
                .single();
        int count = snapshot(id, r.criteria());
        audit.log("CATEGORY_CREATED", "CATEGORY", id, Map.of("name", r.name(), "incidents", count));
        return id;
    }

    @Transactional
    public void update(long id, CategoryRequest r) {
        int rows = jdbc.sql("""
                        UPDATE category SET name = :name, description = :description,
                               criteria = CAST(:criteria AS jsonb)
                        WHERE id = :id""")
                .param("name", r.name().strip())
                .param("description", r.description())
                .param("criteria", json.writeValueAsString(r.criteria()))
                .param("id", id)
                .update();
        if (rows == 0) {
            throw new NotFoundException("Категория", id);
        }
        int count = snapshot(id, r.criteria());
        audit.log("CATEGORY_UPDATED", "CATEGORY", id, Map.of("incidents", count));
    }

    /** Пересчитать срез по сохранённому фильтру. */
    @Transactional
    public Category refresh(long id) {
        Category c = get(id);
        int count = snapshot(id, c.criteria());
        audit.log("CATEGORY_REFRESHED", "CATEGORY", id, Map.of("incidents", count));
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (jdbc.sql("DELETE FROM category WHERE id = :id").param("id", id).update() == 0) {
            throw new NotFoundException("Категория", id);
        }
        audit.log("CATEGORY_DELETED", "CATEGORY", id, Map.of());
    }

    private int snapshot(long categoryId, IncidentCriteria criteria) {
        jdbc.sql("DELETE FROM category_incident WHERE category_id = :id").param("id", categoryId).update();
        var where = IncidentSql.where(criteria);
        int count = jdbc.sql("INSERT INTO category_incident (category_id, incident_id) SELECT :categoryId, i.id "
                        + IncidentSql.BASE_FROM + where.sql())
                .params(where.params())
                .param("categoryId", categoryId)
                .update();
        jdbc.sql("UPDATE category SET snapshot_at = now() WHERE id = :id").param("id", categoryId).update();
        return count;
    }

    private Category toCategory(Row r) {
        return new Category(r.id(), r.code(), r.name(), r.description(),
                json.readValue(r.criteria(), IncidentCriteria.class),
                r.createdAt(), r.createdBy(), r.snapshotAt(), r.incidentCount());
    }

    private static String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
