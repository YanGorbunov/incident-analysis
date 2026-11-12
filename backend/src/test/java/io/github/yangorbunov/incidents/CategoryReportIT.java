package io.github.yangorbunov.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import io.github.yangorbunov.incidents.category.CategoryService;
import io.github.yangorbunov.incidents.category.CategoryService.CategoryRequest;
import io.github.yangorbunov.incidents.incident.IncidentCriteria;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentRequest;
import io.github.yangorbunov.incidents.incident.IncidentDtos.Source;
import io.github.yangorbunov.incidents.incident.IncidentService;
import io.github.yangorbunov.incidents.reference.ReferenceRepository;
import io.github.yangorbunov.incidents.report.ReportFormat.Field;
import io.github.yangorbunov.incidents.report.ReportService;

/**
 * Проверка формул ВКР на известных данных:
 * 4 инцидента приоритета «Низкий», 3 закрыты за 2, 4 и 6 часов.
 * P = 3 / 4 × 100% = 75%, Tср = (2 + 4 + 6) / 3 = 4 ч.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@WithMockUser(username = "analyst", roles = "ANALYST")
class CategoryReportIT {

    @Autowired
    IncidentService incidents;

    @Autowired
    CategoryService categories;

    @Autowired
    ReportService reports;

    @Autowired
    ReferenceRepository reference;

    @Test
    void formulasFromThesisAndExport() throws Exception {
        long low = reference.priorityByCode("04").orElseThrow().id();
        long closed = reference.statusByCode("06").orElseThrow().id();
        long open = reference.statusByCode("01").orElseThrow().id();
        var base = OffsetDateTime.parse("2024-02-01T10:00:00+03:00");

        for (int hours : new int[] {2, 4, 6}) {
            incidents.create(new IncidentRequest("Закрытый " + hours, "Описание", "Решено", base, base,
                    base.plusHours(hours), base.plusHours(hours), low, closed, null, List.of(), null, null),
                    Source.MANUAL);
        }
        incidents.create(new IncidentRequest("Открытый", "Описание", null, base, null, null, null, low, open,
                null, List.of(), null, null), Source.MANUAL);

        var criteria = new IncidentCriteria(null, base.minusDays(1), base.plusDays(1), List.of(low),
                null, null, null, null, null, null);
        long categoryId = categories.create(new CategoryRequest("Низкие за февраль", null, criteria));

        var category = categories.get(categoryId);
        assertThat(category.incidentCount()).isEqualTo(4);
        assertThat(category.criteria().priorityIds()).containsExactly(low);

        var report = reports.build(categoryId);
        var s = report.summary();
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.closed()).isEqualTo(3);
        assertThat(s.closedPct()).isEqualTo(75.0);
        assertThat(s.avgResolutionSeconds()).isEqualTo(4 * 3600);
        assertThat(s.medianResolutionSeconds()).isEqualTo(4 * 3600);
        assertThat(report.byPriority()).hasSize(5);
        assertThat(report.incidents()).hasSize(4);

        // Срез зафиксирован: новый подходящий инцидент не попадает в отчёт до «Обновить»
        incidents.create(new IncidentRequest("Поздний", "Описание", null, base.plusHours(1), null, null, null,
                low, open, null, List.of(), null, null), Source.MANUAL);
        assertThat(reports.build(categoryId).summary().total()).isEqualTo(4);
        assertThat(categories.refresh(categoryId).incidentCount()).isEqualTo(5);

        var docx = reports.export(categoryId, ReportService.Format.DOCX, List.of(Field.CODE, Field.TITLE));
        try (var doc = new XWPFDocument(new ByteArrayInputStream(docx.content()))) {
            assertThat(doc.getTables()).isNotEmpty();
            assertThat(doc.getAllPictures()).hasSize(1);
        }

        var xlsx = reports.export(categoryId, ReportService.Format.XLSX, List.of());
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.content()))) {
            assertThat(wb.getSheet("Инциденты").getLastRowNum()).isEqualTo(5);
            assertThat(wb.getSheet("Сводка").getDrawingPatriarch().getCharts()).hasSize(1);
        }
    }
}
