package io.github.yangorbunov.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import io.github.yangorbunov.incidents.common.ConflictException;
import io.github.yangorbunov.incidents.common.ValidationException;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentRequest;
import io.github.yangorbunov.incidents.incident.IncidentDtos.Source;
import io.github.yangorbunov.incidents.incident.IncidentService;
import io.github.yangorbunov.incidents.reference.ReferenceRepository;

@SpringBootTest
@Import(TestcontainersConfig.class)
@WithMockUser(username = "tester", roles = "ANALYST")
class IncidentLifecycleIT {

    @Autowired
    IncidentService incidents;

    @Autowired
    ReferenceRepository reference;

    long status(String code) {
        return reference.statusByCode(code).orElseThrow().id();
    }

    long priority(String code) {
        return reference.priorityByCode(code).orElseThrow().id();
    }

    IncidentRequest request(OffsetDateTime registered, String statusCode, Integer version) {
        return new IncidentRequest("Сбой сервера", "Описание", null, registered, null, null, null,
                priority("03"), status(statusCode), null, List.of(), null, version);
    }

    @Test
    void statusTransitionsDriveTimestampsAndHistory() {
        var registered = OffsetDateTime.now().minusHours(5).truncatedTo(ChronoUnit.MINUTES);
        long id = incidents.create(request(registered, "01", null), Source.MANUAL);

        var created = incidents.get(id);
        assertThat(created.code()).matches("\\d{6}\\.\\d{4}");
        assertThat(created.firstResponseAt()).isNull();
        assertThat(created.closedAt()).isNull();

        incidents.update(id, request(registered, "04", created.version()));
        var inWork = incidents.get(id);
        assertThat(inWork.firstResponseAt()).isNotNull();
        assertThat(inWork.resolvedAt()).isNull();

        incidents.update(id, request(registered, "06", inWork.version()));
        var closed = incidents.get(id);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.resolvedAt()).isNotNull();
        assertThat(closed.firstResponseAt()).isEqualTo(inWork.firstResponseAt());
        assertThat(closed.history()).extracting(h -> h.toStatus())
                .containsExactly("Новый", "В работе", "Закрыт");

        // Переоткрытие стирает момент закрытия и решения
        incidents.update(id, request(registered, "04", closed.version()));
        var reopened = incidents.get(id);
        assertThat(reopened.closedAt()).isNull();
        assertThat(reopened.resolvedAt()).isNull();
    }

    @Test
    void staleVersionIsRejected() {
        long id = incidents.create(request(OffsetDateTime.now().minusHours(1), "01", null), Source.MANUAL);
        int version = incidents.get(id).version();
        incidents.update(id, request(OffsetDateTime.now().minusHours(1), "03", version));

        assertThatThrownBy(() -> incidents.update(id, request(OffsetDateTime.now().minusHours(1), "04", version)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registrationInFutureIsRejected() {
        assertThatThrownBy(() -> incidents.create(request(OffsetDateTime.now().plusDays(1), "01", null),
                Source.MANUAL))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void codesAreUniqueUnderConcurrency() throws Exception {
        var registered = OffsetDateTime.parse("2025-03-30T12:00:00+03:00");
        Set<String> codes = ConcurrentHashMap.newKeySet();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 40; i++) {
                pool.submit(() -> {
                    long id = incidents.create(request(registered, "01", null), Source.MANUAL);
                    codes.add(incidents.get(id).code());
                    return id;
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(codes).hasSize(40);
        // 30 марта 2025 → 250330 (в ВКР пример ошибочно закодирован как 300224)
        assertThat(codes).allMatch(c -> c.startsWith("250330."));
        assertThat(codes).contains("250330.0001", "250330.0040");
    }
}
