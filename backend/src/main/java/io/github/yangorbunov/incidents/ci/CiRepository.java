package io.github.yangorbunov.incidents.ci;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.yangorbunov.incidents.ci.CiDtos.CiDetails;
import io.github.yangorbunov.incidents.ci.CiDtos.CiListItem;
import io.github.yangorbunov.incidents.ci.CiDtos.CiRequest;
import io.github.yangorbunov.incidents.common.PageResponse;
import io.github.yangorbunov.incidents.reference.ReferenceDtos.Software;

@Repository
public class CiRepository {

    private static final Map<String, String> SORT = Map.of(
            "code", "c.code",
            "name", "c.name",
            "type", "t.name",
            "institution", "i.name",
            "incidents", "incident_count");

    private static final String DETAILS_SELECT = """
            SELECT c.id, c.code, c.external_id, c.name, c.ci_type_id, t.name AS ci_type_name,
                   c.institution_id, i.name AS institution_name, host(c.ip_address) AS ip_address,
                   c.technical_specs, c.created_at, c.updated_at, c.version
            FROM configuration_item c
            JOIN ci_type t ON t.id = c.ci_type_id
            JOIN institution i ON i.id = c.institution_id
            """;

    private final JdbcClient jdbc;

    public CiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<CiListItem> search(String q, Long ciTypeId, Long institutionId,
                                           String sort, boolean desc, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (c.name ILIKE :q OR c.code ILIKE :q OR host(c.ip_address) ILIKE :q)");
            params.put("q", "%" + q.strip() + "%");
        }
        if (ciTypeId != null) {
            where.append(" AND c.ci_type_id = :ciTypeId");
            params.put("ciTypeId", ciTypeId);
        }
        if (institutionId != null) {
            where.append(" AND c.institution_id = :institutionId");
            params.put("institutionId", institutionId);
        }
        String from = """
                FROM configuration_item c
                JOIN ci_type t ON t.id = c.ci_type_id
                JOIN institution i ON i.id = c.institution_id
                """;
        long total = jdbc.sql("SELECT count(*) " + from + where).params(params).query(Long.class).single();
        String order = SORT.getOrDefault(sort, "c.code") + (desc ? " DESC" : " ASC") + ", c.id";
        List<CiListItem> items = jdbc.sql("""
                        SELECT c.id, c.code, c.name, c.ci_type_id, t.name AS ci_type_name, c.institution_id,
                               i.name AS institution_name, host(c.ip_address) AS ip_address,
                               (SELECT count(*) FROM incident_ci ic WHERE ic.ci_id = c.id) AS incident_count
                        """ + from + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(CiListItem.class)
                .list();
        return new PageResponse<>(items, total, page, size);
    }

    public Optional<CiDetails> find(long id) {
        return jdbc.sql(DETAILS_SELECT + " WHERE c.id = :id").param("id", id)
                .query((rs, n) -> new CiDetails(
                        rs.getLong("id"), rs.getString("code"), rs.getString("external_id"), rs.getString("name"),
                        rs.getLong("ci_type_id"), rs.getString("ci_type_name"),
                        rs.getLong("institution_id"), rs.getString("institution_name"),
                        rs.getString("ip_address"), rs.getString("technical_specs"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getInt("version"), List.of()))
                .optional()
                .map(d -> d.withSoftware(software(id)));
    }

    public Optional<Long> findIdByExternalId(String externalId) {
        return jdbc.sql("SELECT id FROM configuration_item WHERE external_id = :ext")
                .param("ext", externalId).query(Long.class).optional();
    }

    public List<Software> software(long ciId) {
        return jdbc.sql("""
                        SELECT s.* FROM software s JOIN ci_software cs ON cs.software_id = s.id
                        WHERE cs.ci_id = :id ORDER BY s.name""")
                .param("id", ciId).query(Software.class).list();
    }

    public long insert(String code, CiRequest r) {
        return jdbc.sql("""
                        INSERT INTO configuration_item (code, external_id, name, ci_type_id, institution_id,
                                                        ip_address, technical_specs)
                        VALUES (:code, :externalId, :name, :ciTypeId, :institutionId,
                                CAST(:ipAddress AS inet), :technicalSpecs)
                        RETURNING id""")
                .param("code", code)
                .param("externalId", r.externalId())
                .param("name", r.name())
                .param("ciTypeId", r.ciTypeId())
                .param("institutionId", r.institutionId())
                .param("ipAddress", blankToNull(r.ipAddress()))
                .param("technicalSpecs", r.technicalSpecs())
                .query(Long.class)
                .single();
    }

    /** Ведомство меняется, а код ВВВ.XXXX — нет: коды неизменяемы. Возвращает число обновлённых строк. */
    public int update(long id, int version, CiRequest r) {
        return jdbc.sql("""
                        UPDATE configuration_item
                        SET external_id = :externalId, name = :name, ci_type_id = :ciTypeId,
                            institution_id = :institutionId, ip_address = CAST(:ipAddress AS inet),
                            technical_specs = :technicalSpecs, updated_at = now(), version = version + 1
                        WHERE id = :id AND version = :version""")
                .param("externalId", r.externalId())
                .param("name", r.name())
                .param("ciTypeId", r.ciTypeId())
                .param("institutionId", r.institutionId())
                .param("ipAddress", blankToNull(r.ipAddress()))
                .param("technicalSpecs", r.technicalSpecs())
                .param("id", id)
                .param("version", version)
                .update();
    }

    public void replaceSoftware(long ciId, List<Long> softwareIds) {
        jdbc.sql("DELETE FROM ci_software WHERE ci_id = :id").param("id", ciId).update();
        for (Long sid : softwareIds.stream().distinct().toList()) {
            jdbc.sql("INSERT INTO ci_software (ci_id, software_id) VALUES (:ci, :sw)")
                    .param("ci", ciId).param("sw", sid).update();
        }
    }

    public boolean usedByIncidents(long id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM incident_ci WHERE ci_id = :id)")
                .param("id", id).query(Boolean.class).single();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM configuration_item WHERE id = :id").param("id", id).update();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
