package io.github.yangorbunov.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import io.github.yangorbunov.incidents.incident.IncidentService;
import io.github.yangorbunov.incidents.tracker.ImportLog.Outcome;
import io.github.yangorbunov.incidents.tracker.ImportService;

/** Импорт из встроенных данных трекера (tracker/fixtures.json) поверх демо-справочников. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfig.class)
@WithMockUser(username = "analyst", roles = "ANALYST")
class ImportIT {

    @Autowired
    ImportService importService;

    @Autowired
    IncidentService incidents;

    @Test
    void importIsIdempotentAndPullsMissingCis() {
        var first = importService.importIncident("TASK-300002");
        assertThat(first.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(first.warnings()).anyMatch(w -> w.contains("ci-5003"));

        var details = incidents.get(first.entityId());
        assertThat(details.cis()).extracting(c -> c.name()).containsExactly("Принтер бланков МФЦ Новосибирск");
        assertThat(details.code()).startsWith("261111.");

        var second = importService.importIncident("TASK-300002");
        assertThat(second.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(second.entityId()).isEqualTo(first.entityId());
    }

    @Test
    void closedIncidentKeepsTrackerTimestamps() {
        var result = importService.importIncident("TASK-300001");
        var d = incidents.get(result.entityId());
        assertThat(d.firstResponseAt()).isNotNull();
        assertThat(d.resolvedAt()).isBefore(d.closedAt());
        assertThat(d.assigneeId()).isNotNull();
    }

    @Test
    void unknownAssigneeIsWarningNotError() {
        var result = importService.importIncident("TASK-300003");
        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(result.warnings()).anyMatch(w -> w.contains("u-unknown"));
    }

    @Test
    void unknownIdIsNotFound() {
        assertThat(importService.importIncident("NOPE-1").outcome()).isEqualTo(Outcome.NOT_FOUND);
    }
}
