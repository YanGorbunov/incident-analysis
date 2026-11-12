package io.github.yangorbunov.incidents.tracker;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.tracker.ImportLog.Kind;
import io.github.yangorbunov.incidents.tracker.ImportLog.Outcome;
import io.github.yangorbunov.incidents.tracker.ImportService.ImportResult;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService service;
    private final ImportLog log;
    private final MeterRegistry meters;

    public ImportController(ImportService service, ImportLog log, MeterRegistry meters) {
        this.service = service;
        this.log = log;
        this.meters = meters;
    }

    @PostMapping("/incidents/{externalId}")
    public ResponseEntity<ImportResult> importIncident(@PathVariable String externalId, Authentication auth) {
        return run(Kind.INCIDENT, externalId, auth, () -> service.importIncident(externalId));
    }

    @PostMapping("/cis/{externalId}")
    public ResponseEntity<ImportResult> importCi(@PathVariable String externalId, Authentication auth) {
        return run(Kind.CI, externalId, auth, () -> service.importCi(externalId));
    }

    @GetMapping("/runs")
    public List<ImportLog.ImportRun> runs() {
        return log.recent(50);
    }

    private ResponseEntity<ImportResult> run(Kind kind, String externalId, Authentication auth,
                                             java.util.function.Supplier<ImportResult> action) {
        long started = System.nanoTime();
        try {
            ImportResult result = action.get();
            long ms = (System.nanoTime() - started) / 1_000_000;
            log.record(kind, externalId, result.outcome(), String.join("; ", result.warnings()),
                    result.entityId(), auth.getName(), ms);
            count(kind, result.outcome());
            return result.outcome() == Outcome.NOT_FOUND
                    ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(result)
                    : ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - started) / 1_000_000;
            log.record(kind, externalId, Outcome.FAILED, e.getMessage(), null, auth.getName(), ms);
            count(kind, Outcome.FAILED);
            throw e;
        }
    }

    private void count(Kind kind, Outcome outcome) {
        meters.counter("incidents.import", "kind", kind.name(), "outcome", outcome.name()).increment();
    }
}
