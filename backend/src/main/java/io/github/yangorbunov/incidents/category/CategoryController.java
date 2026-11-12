package io.github.yangorbunov.incidents.category;

import java.util.List;
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

import io.github.yangorbunov.incidents.category.CategoryService.Category;
import io.github.yangorbunov.incidents.category.CategoryService.CategoryRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Category> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Category get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody CategoryRequest r) {
        return Map.of("id", service.create(r));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody CategoryRequest r) {
        service.update(id, r);
    }

    @PostMapping("/{id}/refresh")
    public Category refresh(@PathVariable long id) {
        return service.refresh(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
