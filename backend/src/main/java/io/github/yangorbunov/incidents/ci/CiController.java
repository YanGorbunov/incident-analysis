package io.github.yangorbunov.incidents.ci;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.ci.CiDtos.CiDetails;
import io.github.yangorbunov.incidents.ci.CiDtos.CiListItem;
import io.github.yangorbunov.incidents.ci.CiDtos.CiRequest;
import io.github.yangorbunov.incidents.common.PageResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cis")
public class CiController {

    private final CiService service;
    private final CiRepository repo;

    public CiController(CiService service, CiRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @GetMapping
    public PageResponse<CiListItem> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Long ciTypeId,
                                           @RequestParam(required = false) Long institutionId,
                                           @RequestParam(defaultValue = "code") String sort,
                                           @RequestParam(defaultValue = "false") boolean desc,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return repo.search(q, ciTypeId, institutionId, sort, desc, Math.max(page, 0), Math.clamp(size, 1, 200));
    }

    @GetMapping("/{id}")
    public CiDetails get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody CiRequest r) {
        return Map.of("id", service.create(r));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody CiRequest r) {
        service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
