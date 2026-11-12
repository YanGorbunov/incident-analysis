package io.github.yangorbunov.incidents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidentAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentAnalyticsApplication.class, args);
    }
}
