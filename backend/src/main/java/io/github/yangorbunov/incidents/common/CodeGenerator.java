package io.github.yangorbunov.incidents.common;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.yangorbunov.incidents.config.AppProperties;

/**
 * Коды объектов по Табл. 2.3 ВКР.
 * <p>
 * В ВКР коды были «формулами» идентификаторов BIGINT, которые на практике
 * не стыковались с типом ключа. Здесь код — отдельный бизнес-атрибут,
 * а первичный ключ — суррогатный. Счётчик — атомарный upsert, поэтому два
 * параллельных импорта не получат одинаковый номер.
 */
@Component
public class CodeGenerator {

    /** В ВКР формат назван ГГММДД, но описан и нарисован как ДДММГГ; ГГММДД сортируется. */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcClient jdbc;
    private final ZoneId zone;

    public CodeGenerator(JdbcClient jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.zone = properties.zone();
    }

    /** Инцидент: ГГММДД.XXXX — дата регистрации и порядковый номер за сутки. */
    public String incidentCode(OffsetDateTime registeredAt) {
        String day = registeredAt.atZoneSameInstant(zone).format(DAY);
        return day + "." + pad(next("INCIDENT", day), 4);
    }

    /** Конфигурационная единица: ВВВ.XXXX — код ведомства и порядковый номер. */
    public String ciCode(String institutionCode) {
        return institutionCode + "." + pad(next("CI", institutionCode), 4);
    }

    /** Сотрудник: ДДДД.XXXX — код должности без точки и порядковый номер. */
    public String employeeCode(String postCode) {
        String prefix = postCode.replace(".", "");
        return prefix + "." + pad(next("EMPLOYEE", prefix), 4);
    }

    /** Должность: ОО.XX — код отдела и порядковый номер. */
    public String postCode(String departmentCode) {
        return departmentCode + "." + pad(next("POST", departmentCode), 2);
    }

    /** Порядковые справочники без префикса: отдел (XX), ведомство (XXX) и т.п. */
    public String sequential(String scope, int width) {
        return pad(next(scope, ""), width);
    }

    /** Вызывается внутри транзакции вызывающего сервиса. */
    int next(String scope, String prefix) {
        return jdbc.sql("""
                        INSERT INTO code_counter (scope, prefix, last_value) VALUES (:scope, :prefix, 1)
                        ON CONFLICT (scope, prefix) DO UPDATE SET last_value = code_counter.last_value + 1
                        RETURNING last_value
                        """)
                .param("scope", scope)
                .param("prefix", prefix)
                .query(Integer.class)
                .single();
    }

    static String pad(int value, int width) {
        String s = Integer.toString(value);
        if (s.length() > width) {
            throw new ConflictException("Исчерпан диапазон кодов (" + width + " разрядов)");
        }
        return "0".repeat(width - s.length()) + s;
    }
}
