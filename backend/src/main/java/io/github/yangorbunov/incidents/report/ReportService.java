package io.github.yangorbunov.incidents.report;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.category.CategoryService;
import io.github.yangorbunov.incidents.config.AppProperties;
import io.github.yangorbunov.incidents.report.ReportDtos.Author;
import io.github.yangorbunov.incidents.report.ReportDtos.Report;
import io.github.yangorbunov.incidents.report.ReportFormat.Field;
import io.github.yangorbunov.incidents.security.AppUserRepository;

@Service
public class ReportService {

    public enum Format { DOCX, XLSX }

    public record Export(byte[] content, String filename, String contentType) {
    }

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("ru"));

    private final CategoryService categories;
    private final AnalyticsRepository analytics;
    private final AppUserRepository users;
    private final DocxReportWriter docx;
    private final XlsxReportWriter xlsx;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(CategoryService categories, AnalyticsRepository analytics, AppUserRepository users,
                         DocxReportWriter docx, XlsxReportWriter xlsx, AuditService audit, Clock clock,
                         AppProperties properties) {
        this.categories = categories;
        this.analytics = analytics;
        this.users = users;
        this.docx = docx;
        this.xlsx = xlsx;
        this.audit = audit;
        this.clock = clock;
        this.zone = properties.zone();
    }

    /** Один снимок данных на весь отчёт (REPEATABLE READ) — цифры в шапке и в таблице согласованы. */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Report build(long categoryId) {
        var category = categories.get(categoryId);
        return new Report(
                category,
                OffsetDateTime.now(clock),
                author(),
                analytics.summary(categoryId),
                analytics.byPriority(categoryId),
                analytics.byStatus(categoryId),
                analytics.byInstitution(categoryId),
                analytics.topCis(categoryId, 10),
                analytics.byAssignee(categoryId),
                analytics.weeklyTrend(categoryId),
                analytics.incidents(categoryId));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Export export(long categoryId, Format format, List<Field> fields) {
        Report report = build(categoryId);
        List<Field> cols = fields == null || fields.isEmpty() ? defaultFields() : fields;
        String base = "report-" + report.category().code() + "-"
                + report.generatedAt().atZoneSameInstant(zone).toLocalDate();
        Export result = switch (format) {
            case DOCX -> new Export(docx.write(report, cols, zone), base + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case XLSX -> new Export(xlsx.write(report, cols, zone), base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
        audit.log("REPORT_EXPORTED", "CATEGORY", categoryId, Map.of("format", format, "rows", report.incidents().size()));
        return result;
    }

    /** Поля по умолчанию — как на макете Рис. 3.13. */
    public static List<Field> defaultFields() {
        return List.of(Field.CODE, Field.TITLE, Field.REGISTERED_AT, Field.CLOSED_AT,
                Field.PRIORITY, Field.STATUS, Field.ASSIGNEE);
    }

    /** «Сводный отчёт о результатах анализа инцидентов от «14» февраля 2026 г.» */
    static String titleFor(OffsetDateTime at, ZoneId zone) {
        var local = at.atZoneSameInstant(zone);
        String monthYear = local.format(MONTH_YEAR);
        return "Сводный отчёт о результатах анализа инцидентов от «" + local.getDayOfMonth() + "» "
                + monthYear + " г.";
    }

    private Author author() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return new Author(null, null, null);
        }
        return users.findView(auth.getName())
                .map(u -> new Author(u.username(), u.employeeName() != null ? u.employeeName() : u.username(),
                        u.postName()))
                .orElse(new Author(auth.getName(), auth.getName(), null));
    }
}
