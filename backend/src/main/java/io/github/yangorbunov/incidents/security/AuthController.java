package io.github.yangorbunov.incidents.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yangorbunov.incidents.common.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Пароль проверен, ждём второй фактор. Живёт в сессии не дольше 5 минут. */
    record PendingMfa(Authentication authentication, Instant expiresAt) implements java.io.Serializable {
    }

    private static final String PENDING_MFA = AuthController.class.getName() + ".PENDING_MFA";
    private static final String PENDING_TOTP_SECRET = AuthController.class.getName() + ".PENDING_TOTP";

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record MfaRequest(@NotBlank String code) {
    }

    public record LoginResponse(boolean mfaRequired, AppUserRepository.UserView user) {
    }

    public record TotpSetupResponse(String secret, String otpauthUri) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    private final AuthService auth;
    private final AppUserRepository users;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    private final Clock clock;

    public AuthController(AuthService auth, AppUserRepository users,
                          SecurityContextRepository contextRepository, Clock clock) {
        this.auth = auth;
        this.users = users;
        this.contextRepository = contextRepository;
        this.clock = clock;
    }

    /** SPA вызывает при старте, чтобы получить cookie XSRF-TOKEN. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        return switch (auth.checkPassword(body.username(), body.password())) {
            case AuthService.PasswordResult.Failure f -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("detail", f.message()));
            case AuthService.PasswordResult.Success s when s.mfaRequired() -> {
                HttpSession session = request.getSession(true);
                session.setAttribute(PENDING_MFA,
                        new PendingMfa(s.authentication(), clock.instant().plusSeconds(300)));
                yield ResponseEntity.ok(new LoginResponse(true, null));
            }
            case AuthService.PasswordResult.Success s -> {
                establish(s.authentication(), request, response);
                yield ResponseEntity.ok(new LoginResponse(false, me(s.authentication())));
            }
        };
    }

    @PostMapping("/mfa")
    public ResponseEntity<?> mfa(@Valid @RequestBody MfaRequest body,
                                 HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        PendingMfa pending = session == null ? null : (PendingMfa) session.getAttribute(PENDING_MFA);
        if (pending == null || pending.expiresAt().isBefore(clock.instant())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("detail", "Сессия входа истекла, введите логин и пароль заново"));
        }
        if (!auth.verifyTotp(pending.authentication().getName(), body.code())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Неверный код"));
        }
        session.removeAttribute(PENDING_MFA);
        establish(pending.authentication(), request, response);
        return ResponseEntity.ok(new LoginResponse(false, me(pending.authentication())));
    }

    @GetMapping("/me")
    public AppUserRepository.UserView me(Authentication authentication) {
        return users.findView(authentication.getName()).orElseThrow();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                               Authentication authentication) {
        auth.changePassword(authentication.getName(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/totp/setup")
    public TotpSetupResponse totpSetup(Authentication authentication, HttpSession session) {
        String secret = Totp.generateSecret();
        session.setAttribute(PENDING_TOTP_SECRET, secret);
        return new TotpSetupResponse(secret, auth.totpUri(authentication.getName(), secret));
    }

    @PostMapping("/totp/confirm")
    public ResponseEntity<Void> totpConfirm(@Valid @RequestBody MfaRequest body,
                                            Authentication authentication, HttpSession session) {
        String secret = (String) session.getAttribute(PENDING_TOTP_SECRET);
        if (secret == null) {
            throw new ValidationException("Сначала начните настройку второго фактора");
        }
        auth.enableTotp(authentication.getName(), secret, body.code());
        session.removeAttribute(PENDING_TOTP_SECRET);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/totp")
    public ResponseEntity<Void> totpDisable(@Valid @RequestBody MfaRequest body, Authentication authentication) {
        auth.disableTotp(authentication.getName(), body.code());
        return ResponseEntity.noContent().build();
    }

    private void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // защита от фиксации сессии
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
