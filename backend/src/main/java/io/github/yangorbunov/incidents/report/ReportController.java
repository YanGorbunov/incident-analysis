package io.github.yangorbunov.incidents.report;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.report.ReportDtos.Report;
import io.github.yangorbunov.incidents.report.ReportFormat.Field;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    public record FieldOption(Field id, String title) {
    }

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/fields")
    public List<FieldOption> fields() {
        return Arrays.stream(Field.values()).map(f -> new FieldOption(f, f.title)).toList();
    }

    @GetMapping("/{categoryId}")
    public Report report(@PathVariable long categoryId) {
        return service.build(categoryId);
    }

    @GetMapping("/{categoryId}/export")
    public ResponseEntity<byte[]> export(@PathVariable long categoryId,
                                         @RequestParam ReportService.Format format,
                                         @RequestParam(required = false) List<Field> fields) {
        var export = service.export(categoryId, format, fields);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.filename(), StandardCharsets.UTF_8).build().toString())
                .body(export.content());
    }
}
