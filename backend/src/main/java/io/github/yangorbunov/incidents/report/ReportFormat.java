package io.github.yangorbunov.incidents.report;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;

import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentListItem;

/** Форматирование значений отчёта и набор колонок таблицы инцидентов. */
public final class ReportFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    private ReportFormat() {
    }

    /** Колонки таблицы инцидентов, которые аналитик выбирает в поле «Поля» (Рис. 3.13). */
    public enum Field {
        CODE("Идентификатор", 14, (i, z) -> i.code()),
        TITLE("Наименование", 40, (i, z) -> i.title()),
        REGISTERED_AT("Регистрация", 16, (i, z) -> dateTime(i.registeredAt(), z)),
        CLOSED_AT("Закрытие", 16, (i, z) -> dateTime(i.closedAt(), z)),
        PRIORITY("Приоритет", 14, (i, z) -> i.priorityName()),
        STATUS("Статус", 14, (i, z) -> i.statusName()),
        ASSIGNEE("Ответственный", 20, (i, z) -> orDash(i.assigneeName())),
        CIS("КЕ", 30, (i, z) -> orDash(i.cis())),
        INSTITUTIONS("Ведомства", 30, (i, z) -> orDash(i.institutions())),
        RESOLUTION_TIME("Время решения", 18,
                (i, z) -> i.resolutionSeconds() == null ? "—" : duration(i.resolutionSeconds())),
        SLA("SLA", 12, (i, z) -> i.slaBreached() ? "нарушен" : "в норме");

        public final String title;
        public final int width;
        private final BiFunction<IncidentListItem, ZoneId, String> extractor;

        Field(String title, int width, BiFunction<IncidentListItem, ZoneId, String> extractor) {
            this.title = title;
            this.width = width;
            this.extractor = extractor;
        }

        public String value(IncidentListItem item, ZoneId zone) {
            return extractor.apply(item, zone);
        }
    }

    public static String dateTime(OffsetDateTime t, ZoneId zone) {
        return t == null ? "—" : t.atZoneSameInstant(zone).format(DATE_TIME);
    }

    /** «5 дн. 1 ч. 10 мин.» — как на макете сводного отчёта (Рис. 2.6). */
    public static String duration(Long seconds) {
        if (seconds == null) {
            return "—";
        }
        Duration d = Duration.ofSeconds(seconds);
        long days = d.toDays();
        int hours = d.toHoursPart();
        int minutes = d.toMinutesPart();
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" дн. ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append(" ч. ");
        }
        sb.append(minutes).append(" мин.");
        return sb.toString();
    }

    public static String percent(Double value) {
        return value == null ? "—" : String.format("%.1f %%", value).replace('.', ',');
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
