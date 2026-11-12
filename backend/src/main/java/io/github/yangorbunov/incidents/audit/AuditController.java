package io.github.yangorbunov.incidents.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.common.PageResponse;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditService.AuditEvent> find(@RequestParam(required = false) String username,
                                                      @RequestParam(required = false) String action,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return audit.find(username, action, Math.max(page, 0), Math.clamp(size, 1, 500));
    }

    @GetMapping("/actions")
    public List<String> actions() {
        return audit.actions();
    }
}
