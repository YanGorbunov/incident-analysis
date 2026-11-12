package io.github.yangorbunov.incidents.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReportFormatTest {

    @Test
    void durationLikeMockup() {
        // «5 дн. 1 ч. 10 мин.» — Рис. 2.6 ВКР
        assertEquals("5 дн. 1 ч. 10 мин.", ReportFormat.duration(5 * 86400L + 3600 + 600));
        assertEquals("2 ч. 0 мин.", ReportFormat.duration(7200L));
        assertEquals("45 мин.", ReportFormat.duration(45 * 60L));
        assertEquals("—", ReportFormat.duration(null));
    }

    @Test
    void percentUsesComma() {
        assertEquals("37,5 %", ReportFormat.percent(37.5));
        assertEquals("—", ReportFormat.percent(null));
    }

    @Test
    void titleInGenitive() {
        var at = OffsetDateTime.parse("2024-02-14T10:00:00+03:00");
        assertEquals("Сводный отчёт о результатах анализа инцидентов от «14» февраля 2024 г.",
                ReportService.titleFor(at, ZoneId.of("Europe/Moscow")));
    }

    @Test
    void pieChartIsPng() {
        byte[] png = PieChartRenderer.render(List.of(
                new PieChartRenderer.Slice(3, "#1F4E9A"),
                new PieChartRenderer.Slice(1, "#F45B1F"),
                new PieChartRenderer.Slice(0, "#7B1E3A")), 200);
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
    }
}
