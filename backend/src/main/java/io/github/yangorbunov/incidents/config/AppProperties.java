package io.github.yangorbunov.incidents.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("Europe/Moscow") ZoneId zone,
        @DefaultValue Security security,
        @DefaultValue BootstrapAdmin bootstrapAdmin,
        @DefaultValue Tracker tracker,
        @DefaultValue("false") boolean demoUsers
) {

    public record Security(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration lockDuration,
            @DefaultValue("Incident Analytics") String totpIssuer
    ) {
    }

    public record BootstrapAdmin(
            @DefaultValue("admin") String username,
            String password
    ) {
    }

    public record Tracker(
            @DefaultValue("fixture") Mode mode,
            String baseUrl,
            String token,
            @DefaultValue("10s") Duration timeout,
            Map<String, String> priorityMapping,
            Map<String, String> statusMapping
    ) {
        public enum Mode { FIXTURE, HTTP }

        public Map<String, String> priorityMapping() {
            return priorityMapping == null ? Map.of() : priorityMapping;
        }

        public Map<String, String> statusMapping() {
            return statusMapping == null ? Map.of() : statusMapping;
        }
    }
}
