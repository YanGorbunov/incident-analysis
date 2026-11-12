package io.github.yangorbunov.incidents.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentListItem;
import io.github.yangorbunov.incidents.report.ReportDtos.GroupStat;
import io.github.yangorbunov.incidents.report.ReportDtos.PriorityStat;
import io.github.yangorbunov.incidents.report.ReportDtos.Report;
import io.github.yangorbunov.incidents.report.ReportFormat.Field;

/** Сводный отчёт в DOCX — по макету Рис. 2.6 ВКР, плюс SLA и разрезы. */
@Component
public class DocxReportWriter {

    public byte[] write(Report report, List<Field> fields, ZoneId zone) {
        try (XWPFDocument doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var s = report.summary();

            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setBold(true);
            tr.setFontSize(15);
            tr.setText(ReportService.titleFor(report.generatedAt(), zone));

            labeled(doc, "Должность", report.author().post());
            labeled(doc, "Сотрудник", report.author().name());
            labeled(doc, "Категория", report.category().name());
            labeled(doc, "Количество инцидентов", String.valueOf(s.total()));
            labeled(doc, "Среднее время разрешения", ReportFormat.duration(s.avgResolutionSeconds()));
            labeled(doc, "Медиана / 90-й перцентиль",
                    ReportFormat.duration(s.medianResolutionSeconds()) + " / "
                            + ReportFormat.duration(s.p90ResolutionSeconds()));
            labeled(doc, "Процент закрытых инцидентов", ReportFormat.percent(s.closedPct()));
            labeled(doc, "Среднее время реакции", ReportFormat.duration(s.avgResponseSeconds()));
            labeled(doc, "Соблюдение SLA решения", ReportFormat.percent(s.resolutionSlaCompliancePct())
                    + " (нарушений: " + s.resolutionSlaBreached() + " из " + s.resolutionSlaTotal() + ")");
            labeled(doc, "Соблюдение SLA реакции", ReportFormat.percent(s.responseSlaCompliancePct())
                    + " (нарушений: " + s.responseSlaBreached() + " из " + s.responseSlaTotal() + ")");

            heading(doc, "Распределение по приоритетам");
            byte[] png = PieChartRenderer.render(report.byPriority().stream()
                    .map(p -> new PieChartRenderer.Slice(p.total(), p.color())).toList(), 360);
            XWPFParagraph pic = doc.createParagraph();
            pic.setAlignment(ParagraphAlignment.CENTER);
            pic.createRun().addPicture(new ByteArrayInputStream(png), XWPFDocument.PICTURE_TYPE_PNG,
                    "priorities.png", Units.toEMU(170), Units.toEMU(170));
            priorityTable(doc, report.byPriority(), s.total());

            if (!report.byInstitution().isEmpty()) {
                heading(doc, "По ведомствам");
                groupTable(doc, "Ведомство", report.byInstitution());
            }
            if (!report.topCis().isEmpty()) {
                heading(doc, "Конфигурационные единицы с наибольшим числом инцидентов");
                groupTable(doc, "КЕ", report.topCis());
            }

            heading(doc, "Инциденты");
            incidentTable(doc, report.incidents(), fields, zone);

            doc.write(out);
            return out.toByteArray();
        } catch (IOException | InvalidFormatException e) {
            throw new IllegalStateException("Не удалось сформировать DOCX", e);
        }
    }

    private static void labeled(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(40);
        XWPFRun l = p.createRun();
        l.setText(label + ": ");
        XWPFRun v = p.createRun();
        v.setBold(true);
        v.setText(value == null || value.isBlank() ? "—" : value);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(13);
        r.setText(text);
    }

    private static void priorityTable(XWPFDocument doc, List<PriorityStat> stats, long total) {
        XWPFTable t = doc.createTable(stats.size() + 1, 6);
        t.setWidth("100%");
        header(t, "", "Приоритет", "Инцидентов", "Доля", "Среднее время решения", "Нарушений SLA");
        for (int i = 0; i < stats.size(); i++) {
            PriorityStat p = stats.get(i);
            var row = t.getRow(i + 1);
            row.getCell(0).setColor(p.color().substring(1));
            text(row.getCell(1), p.name(), false);
            text(row.getCell(2), String.valueOf(p.total()), false);
            text(row.getCell(3), total == 0 ? "—" : ReportFormat.percent(100.0 * p.total() / total), false);
            text(row.getCell(4), ReportFormat.duration(p.avgResolutionSeconds()), false);
            text(row.getCell(5), p.slaTotal() == 0 ? "—" : p.slaBreached() + " из " + p.slaTotal(), false);
        }
    }

    private static void groupTable(XWPFDocument doc, String nameTitle, List<GroupStat> stats) {
        XWPFTable t = doc.createTable(stats.size() + 1, 5);
        t.setWidth("100%");
        header(t, nameTitle, "Инцидентов", "Закрыто", "Нарушений SLA", "Среднее время решения");
        for (int i = 0; i < stats.size(); i++) {
            GroupStat g = stats.get(i);
            var row = t.getRow(i + 1);
            text(row.getCell(0), g.name(), false);
            text(row.getCell(1), String.valueOf(g.total()), false);
            text(row.getCell(2), String.valueOf(g.closed()), false);
            text(row.getCell(3), String.valueOf(g.slaBreached()), false);
            text(row.getCell(4), ReportFormat.duration(g.avgResolutionSeconds()), false);
        }
    }

    private static void incidentTable(XWPFDocument doc, List<IncidentListItem> items, List<Field> fields,
                                      ZoneId zone) {
        XWPFTable t = doc.createTable(items.size() + 1, fields.size());
        t.setWidth("100%");
        header(t, fields.stream().map(f -> f.title).toArray(String[]::new));
        for (int i = 0; i < items.size(); i++) {
            var row = t.getRow(i + 1);
            for (int c = 0; c < fields.size(); c++) {
                text(row.getCell(c), fields.get(c).value(items.get(i), zone), false);
            }
        }
    }

    private static void header(XWPFTable t, String... titles) {
        var row = t.getRow(0);
        row.setRepeatHeader(true);
        for (int i = 0; i < titles.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            cell.setColor("EEEEEE");
            text(cell, titles[i], true);
        }
    }

    private static void text(XWPFTableCell cell, String value, boolean bold) {
        XWPFParagraph p = cell.getParagraphs().getFirst();
        XWPFRun r = p.createRun();
        r.setFontSize(9);
        r.setBold(bold);
        r.setText(value == null ? "" : value);
    }
}
