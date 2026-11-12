package io.github.yangorbunov.incidents.tracker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import io.github.yangorbunov.incidents.config.AppProperties;
import io.github.yangorbunov.incidents.tracker.TaskTrackerClient.ExternalCi;
import io.github.yangorbunov.incidents.tracker.TaskTrackerClient.ExternalIncident;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class TrackerClientConfig {

    @Bean
    TaskTrackerClient taskTrackerClient(AppProperties properties, JsonMapper json) {
        var tracker = properties.tracker();
        return switch (tracker.mode()) {
            case FIXTURE -> FixtureClient.load(json);
            case HTTP -> new HttpClient(tracker);
        };
    }

    /** Тестовые данные трекера из classpath:tracker/fixtures.json. */
    static final class FixtureClient implements TaskTrackerClient {

        record Fixtures(List<ExternalIncident> incidents, List<ExternalCi> cis) {
        }

        private final Map<String, ExternalIncident> incidents;
        private final Map<String, ExternalCi> cis;

        private FixtureClient(Fixtures f) {
            this.incidents = f.incidents().stream().collect(Collectors.toMap(ExternalIncident::id, Function.identity()));
            this.cis = f.cis().stream().collect(Collectors.toMap(ExternalCi::id, Function.identity()));
        }

        static FixtureClient load(JsonMapper json) {
            try (var in = new ClassPathResource("tracker/fixtures.json").getInputStream()) {
                return new FixtureClient(json.readValue(in, Fixtures.class));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<ExternalIncident> findIncident(String externalId) {
            return Optional.ofNullable(incidents.get(externalId));
        }

        @Override
        public Optional<ExternalCi> findCi(String externalId) {
            return Optional.ofNullable(cis.get(externalId));
        }
    }

    /**
     * REST-клиент: GET {base}/api/incidents/{id}, GET {base}/api/cis/{id},
     * Bearer-токен. Контракт ответа — {@link ExternalIncident} / {@link ExternalCi};
     * для конкретного трекера (Jira, YouTrack, свой) пишется адаптер-маппер.
     */
    static final class HttpClient implements TaskTrackerClient {

        private final RestClient rest;

        HttpClient(AppProperties.Tracker tracker) {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(tracker.timeout());
            factory.setReadTimeout(tracker.timeout());
            var builder = RestClient.builder()
                    .baseUrl(tracker.baseUrl())
                    .requestFactory(factory);
            if (tracker.token() != null && !tracker.token().isBlank()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tracker.token());
            }
            this.rest = builder.build();
        }

        @Override
        public Optional<ExternalIncident> findIncident(String externalId) {
            return get("/api/incidents/{id}", externalId, ExternalIncident.class);
        }

        @Override
        public Optional<ExternalCi> findCi(String externalId) {
            return get("/api/cis/{id}", externalId, ExternalCi.class);
        }

        private <T> Optional<T> get(String path, String id, Class<T> type) {
            try {
                return Optional.ofNullable(rest.get().uri(path, id).retrieve().body(type));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        }
    }
}
