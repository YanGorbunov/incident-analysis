package io.github.yangorbunov.incidents.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import io.github.yangorbunov.incidents.report.ReportDtos.GroupStat;
import io.github.yangorbunov.incidents.report.ReportDtos.PriorityStat;
import io.github.yangorbunov.incidents.report.ReportDtos.Report;
import io.github.yangorbunov.incidents.report.ReportDtos.TrendPoint;
import io.github.yangorbunov.incidents.report.ReportFormat.Field;

/**
 * Сводный отчёт в XLSX. В отличие от DOCX, числа пишутся числами (часы —
 * десятичной дробью), чтобы аналитик мог считать дальше в Excel; диаграмма
 * — нативная диаграмма Excel, а не картинка.
 */
@Component
public class XlsxReportWriter {

    public byte[] write(Report report, List<Field> fields, ZoneId zone) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            summarySheet(wb, st, report, zone);
            incidentsSheet(wb, st, report, fields, zone);
            groupSheet(wb, st, "Ведомства", "Ведомство", report.byInstitution());
            groupSheet(wb, st, "КЕ", "Конфигурационная единица", report.topCis());
            groupSheet(wb, st, "Ответственные", "Ответственный", report.byAssignee());
            trendSheet(wb, st, report.trend());
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать XLSX", e);
        }
    }

    private record Styles(CellStyle bold, CellStyle header, CellStyle title) {
        Styles(XSSFWorkbook wb) {
            this(style(wb, true, 11), headerStyle(wb), style(wb, true, 14));
        }

        private static CellStyle style(XSSFWorkbook wb, boolean bold, int size) {
            var font = wb.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            CellStyle cs = wb.createCellStyle();
            cs.setFont(font);
            return cs;
        }

        private static CellStyle headerStyle(XSSFWorkbook wb) {
            CellStyle cs = style(wb, true, 11);
            cs.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cs.setWrapText(true);
            return cs;
        }
    }

    private static void summarySheet(XSSFWorkbook wb, Styles st, Report r, ZoneId zone) {
        XSSFSheet sh = wb.createSheet("Сводка");
        var s = r.summary();
        int row = 0;
        sh.createRow(row++).createCell(0).setCellValue(ReportService.titleFor(r.generatedAt(), zone));
        sh.getRow(0).getCell(0).setCellStyle(st.title());
        row++;
        row = kv(sh, st, row, "Должность", r.author().post());
        row = kv(sh, st, row, "Сотрудник", r.author().name());
        row = kv(sh, st, row, "Категория", r.category().name());
        row = kvNum(sh, st, row, "Количество инцидентов", s.total());
        row = kvNum(sh, st, row, "Закрыто", s.closed());
        row = kvNum(sh, st, row, "Процент закрытых, %", s.closedPct());
        row = kvNum(sh, st, row, "Среднее время разрешения, ч", hours(s.avgResolutionSeconds()));
        row = kvNum(sh, st, row, "Медиана времени разрешения, ч", hours(s.medianResolutionSeconds()));
        row = kvNum(sh, st, row, "90-й перцентиль, ч", hours(s.p90ResolutionSeconds()));
        row = kvNum(sh, st, row, "Среднее время реакции, ч", hours(s.avgResponseSeconds()));
        row = kvNum(sh, st, row, "Соблюдение SLA решения, %", s.resolutionSlaCompliancePct());
        row = kvNum(sh, st, row, "Соблюдение SLA реакции, %", s.responseSlaCompliancePct());
        row++;

        int tableTop = row;
        XSSFRow h = sh.createRow(row++);
        String[] titles = {"Приоритет", "Инцидентов", "Закрыто", "Ср. время решения, ч", "Нарушений SLA"};
        for (int i = 0; i < titles.length; i++) {
            h.createCell(i).setCellValue(titles[i]);
            h.getCell(i).setCellStyle(st.header());
        }
        List<PriorityStat> prio = r.byPriority();
        for (PriorityStat p : prio) {
            XSSFRow x = sh.createRow(row++);
            x.createCell(0).setCellValue(p.name());
            x.createCell(1).setCellValue(p.total());
            x.createCell(2).setCellValue(p.closed());
            setNum(x, 3, hours(p.avgResolutionSeconds()));
            x.createCell(4).setCellValue(p.slaBreached());
        }
        sh.setColumnWidth(0, 36 * 256);
        sh.setColumnWidth(1, 18 * 256);
        for (int i = 2; i < 5; i++) {
            sh.setColumnWidth(i, 16 * 256);
        }

        if (!prio.isEmpty()) {
            XSSFDrawing drawing = sh.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 6, 1, 13, 20);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText("Инциденты по приоритетам");
            chart.setTitleOverlay(false);
            chart.getOrAddLegend().setPosition(LegendPosition.RIGHT);
            XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(sh,
                    new CellRangeAddress(tableTop + 1, tableTop + prio.size(), 0, 0));
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sh,
                    new CellRangeAddress(tableTop + 1, tableTop + prio.size(), 1, 1));
            XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
            data.setVaryColors(true);
            data.addSeries(cats, vals);
            chart.plot(data);
        }
    }

    private static void incidentsSheet(XSSFWorkbook wb, Styles st, Report r, List<Field> fields, ZoneId zone) {
        XSSFSheet sh = wb.createSheet("Инциденты");
        XSSFRow h = sh.createRow(0);
        for (int c = 0; c < fields.size(); c++) {
            h.createCell(c).setCellValue(fields.get(c).title);
            h.getCell(c).setCellStyle(st.header());
            sh.setColumnWidth(c, fields.get(c).width * 256);
        }
        int row = 1;
        for (var item : r.incidents()) {
            XSSFRow x = sh.createRow(row++);
            for (int c = 0; c < fields.size(); c++) {
                Field f = fields.get(c);
                if (f == Field.RESOLUTION_TIME && item.resolutionSeconds() != null) {
                    x.createCell(c).setCellValue(hours(item.resolutionSeconds()));
                } else {
                    x.createCell(c).setCellValue(f.value(item, zone));
                }
            }
        }
        sh.createFreezePane(0, 1);
        if (!fields.isEmpty()) {
            sh.setAutoFilter(new CellRangeAddress(0, Math.max(0, row - 1), 0, fields.size() - 1));
        }
    }

    private static void groupSheet(XSSFWorkbook wb, Styles st, String name, String nameTitle, List<GroupStat> stats) {
        XSSFSheet sh = wb.createSheet(name);
        String[] titles = {nameTitle, "Инцидентов", "Закрыто", "Нарушений SLA", "Ср. время решения, ч"};
        XSSFRow h = sh.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            h.createCell(i).setCellValue(titles[i]);
            h.getCell(i).setCellStyle(st.header());
        }
        int row = 1;
        for (GroupStat g : stats) {
            XSSFRow x = sh.createRow(row++);
            x.createCell(0).setCellValue(g.name());
            x.createCell(1).setCellValue(g.total());
            x.createCell(2).setCellValue(g.closed());
            x.createCell(3).setCellValue(g.slaBreached());
            setNum(x, 4, hours(g.avgResolutionSeconds()));
        }
        sh.setColumnWidth(0, 45 * 256);
        for (int i = 1; i < titles.length; i++) {
            sh.setColumnWidth(i, 18 * 256);
        }
        sh.createFreezePane(0, 1);
    }

    private static void trendSheet(XSSFWorkbook wb, Styles st, List<TrendPoint> trend) {
        XSSFSheet sh = wb.createSheet("Динамика");
        XSSFRow h = sh.createRow(0);
        String[] titles = {"Неделя (с)", "Зарегистрировано", "Закрыто"};
        for (int i = 0; i < titles.length; i++) {
            h.createCell(i).setCellValue(titles[i]);
            h.getCell(i).setCellStyle(st.header());
            sh.setColumnWidth(i, 18 * 256);
        }
        int row = 1;
        for (TrendPoint t : trend) {
            XSSFRow x = sh.createRow(row++);
            x.createCell(0).setCellValue(t.weekStart().toString());
            x.createCell(1).setCellValue(t.registered());
            x.createCell(2).setCellValue(t.closed());
        }
    }

    private static int kv(XSSFSheet sh, Styles st, int row, String key, String value) {
        XSSFRow r = sh.createRow(row);
        r.createCell(0).setCellValue(key);
        r.createCell(1).setCellValue(value == null ? "—" : value);
        r.getCell(1).setCellStyle(st.bold());
        return row + 1;
    }

    private static int kvNum(XSSFSheet sh, Styles st, int row, String key, Number value) {
        XSSFRow r = sh.createRow(row);
        r.createCell(0).setCellValue(key);
        setNum(r, 1, value == null ? null : value.doubleValue());
        if (r.getCell(1) != null) {
            r.getCell(1).setCellStyle(st.bold());
        }
        return row + 1;
    }

    private static void setNum(XSSFRow row, int col, Double value) {
        if (value == null) {
            row.createCell(col).setCellValue("—");
        } else {
            row.createCell(col).setCellValue(value);
        }
    }

    private static Double hours(Long seconds) {
        return seconds == null ? null : Math.round(seconds / 36.0) / 100.0;
    }
}
