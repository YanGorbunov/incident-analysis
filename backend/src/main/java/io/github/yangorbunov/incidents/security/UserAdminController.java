package io.github.yangorbunov.incidents.security;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.common.NotFoundException;
import io.github.yangorbunov.incidents.common.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    public record CreateUser(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$", message = "латиница, цифры, . _ -; 3–64 символа")
            String username,
            @NotBlank String password,
            @NotNull Role role,
            Long employeeId
    ) {
    }

    public record UpdateUser(@NotNull Role role, boolean enabled, Long employeeId) {
    }

    public record ResetPassword(@NotBlank String password) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminController(AppUserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @GetMapping
    public List<AppUserRepository.UserView> list() {
        return users.findAllViews();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Long> create(@Valid @RequestBody CreateUser body) {
        PasswordPolicy.check(body.password(), body.username());
        long id = users.insert(body.username(), encoder.encode(body.password()), body.role(), body.employeeId());
        audit.log("USER_CREATED", "USER", id, Map.of("username", body.username(), "role", body.role()));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    @Transactional
    public void update(@PathVariable long id, @Valid @RequestBody UpdateUser body, Authentication me) {
        var user = users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь", id));
        if (user.username().equalsIgnoreCase(me.getName()) && (body.role() != Role.ADMIN || !body.enabled())) {
            throw new ValidationException("Нельзя лишить себя прав администратора или отключить свою учётную запись");
        }
        users.update(id, body.role(), body.enabled(), body.employeeId());
        audit.log("USER_UPDATED", "USER", id, Map.of("role", body.role(), "enabled", body.enabled()));
    }

    @PostMapping("/{id}/password")
    @Transactional
    public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPassword body) {
        var user = users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь", id));
        PasswordPolicy.check(body.password(), user.username());
        users.updatePassword(id, encoder.encode(body.password()));
        audit.log("PASSWORD_RESET", "USER", id, Map.of());
    }

    @PostMapping("/{id}/unlock")
    @Transactional
    public void unlock(@PathVariable long id) {
        users.resetFailures(id);
        audit.log("USER_UNLOCKED", "USER", id, Map.of());
    }

    @DeleteMapping("/{id}/totp")
    @Transactional
    public void resetTotp(@PathVariable long id) {
        users.updateTotpSecret(id, null);
        audit.log("TOTP_RESET", "USER", id, Map.of());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable long id, Authentication me) {
        var user = users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь", id));
        if (user.username().equalsIgnoreCase(me.getName())) {
            throw new ValidationException("Нельзя удалить собственную учётную запись");
        }
        users.delete(id);
        audit.log("USER_DELETED", "USER", id, Map.of("username", user.username()));
    }
}
