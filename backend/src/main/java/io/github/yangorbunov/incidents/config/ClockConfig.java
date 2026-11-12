package io.github.yangorbunov.incidents.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Часы как зависимость: в тестах подменяются фиксированными. */
    @Bean
    Clock clock(AppProperties properties) {
        return Clock.system(properties.zone());
    }
}
