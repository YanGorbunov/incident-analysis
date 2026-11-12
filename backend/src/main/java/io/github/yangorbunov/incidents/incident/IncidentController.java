package io.github.yangorbunov.incidents.incident;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.common.PageResponse;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentDetails;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentListItem;
import io.github.yangorbunov.incidents.incident.IncidentDtos.IncidentRequest;
import io.github.yangorbunov.incidents.incident.IncidentDtos.SearchRequest;
import io.github.yangorbunov.incidents.incident.IncidentDtos.Source;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService service;
    private final IncidentRepository repo;

    public IncidentController(IncidentService service, IncidentRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    /** POST, а не GET: критерии — вложенный объект со списками, в query string он неудобен. */
    @PostMapping("/search")
    public PageResponse<IncidentListItem> search(@Valid @RequestBody SearchRequest r) {
        return repo.search(r.criteria(), r.sort(), r.desc(), r.page(), r.size());
    }

    @GetMapping("/{id}")
    public IncidentDetails get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody IncidentRequest r) {
        return Map.of("id", service.create(r, Source.MANUAL));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody IncidentRequest r) {
        service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
