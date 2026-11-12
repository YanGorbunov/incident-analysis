package io.github.yangorbunov.incidents.security;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yangorbunov.incidents.audit.AuditService;
import io.github.yangorbunov.incidents.common.ValidationException;
import io.github.yangorbunov.incidents.config.AppProperties;

/**
 * Проверка пароля, блокировка после серии неудач, второй фактор.
 * В ВКР (п. 3.4) двухфакторная аутентификация была заявлена, но не
 * спроектирована — здесь это TOTP по RFC 6238.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties.Security props;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager, AppUserRepository users,
                       PasswordEncoder encoder, AuditService audit, AppProperties properties, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.props = properties.security();
        this.clock = clock;
    }

    public sealed interface PasswordResult {
        record Success(Authentication authentication, boolean mfaRequired) implements PasswordResult {
        }

        record Failure(String message) implements PasswordResult {
        }
    }

    public PasswordResult checkPassword(String username, String password) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            var user = users.findByUsername(auth.getName()).orElseThrow();
            users.resetFailures(user.id());
            boolean mfa = user.totpSecret() != null;
            if (!mfa) {
                audit.log(user.username(), "LOGIN_SUCCESS", "USER", user.id(), Map.of());
            }
            return new PasswordResult.Success(auth, mfa);
        } catch (LockedException e) {
            audit.log(username, "LOGIN_LOCKED", "USER", null, Map.of());
            return new PasswordResult.Failure("Учётная запись временно заблокирована после неудачных попыток входа");
        } catch (BadCredentialsException e) {
            users.findByUsername(username).ifPresent(u -> users.registerFailure(
                    u.id(), props.maxFailedAttempts(), OffsetDateTime.now(clock).plus(props.lockDuration())));
            audit.log(username, "LOGIN_FAILURE", "USER", null, Map.of());
            return new PasswordResult.Failure("Неверный логин или пароль");
        } catch (AuthenticationException e) {
            audit.log(username, "LOGIN_FAILURE", "USER", null, Map.of("reason", e.getClass().getSimpleName()));
            return new PasswordResult.Failure("Вход невозможен: учётная запись отключена");
        }
    }

    public boolean verifyTotp(String username, String code) {
        var user = users.findByUsername(username).orElseThrow();
        boolean ok = Totp.verify(user.totpSecret(), code, clock.instant());
        if (ok) {
            users.resetFailures(user.id());
            audit.log(user.username(), "LOGIN_SUCCESS", "USER", user.id(), Map.of("mfa", true));
        } else {
            users.registerFailure(user.id(), props.maxFailedAttempts(),
                    OffsetDateTime.now(clock).plus(props.lockDuration()));
            audit.log(user.username(), "MFA_FAILURE", "USER", user.id(), Map.of());
        }
        return ok;
    }

    public String totpUri(String username, String secret) {
        return Totp.otpauthUri(props.totpIssuer(), username, secret);
    }

    @Transactional
    public void enableTotp(String username, String secret, String code) {
        if (!Totp.verify(secret, code, clock.instant())) {
            throw new ValidationException("Код не подошёл. Проверьте время на телефоне и повторите.");
        }
        var user = users.findByUsername(username).orElseThrow();
        users.updateTotpSecret(user.id(), secret);
        audit.log("TOTP_ENABLED", "USER", user.id(), Map.of());
    }

    @Transactional
    public void disableTotp(String username, String code) {
        var user = users.findByUsername(username).orElseThrow();
        if (user.totpSecret() != null && !Totp.verify(user.totpSecret(), code, clock.instant())) {
            throw new ValidationException("Неверный код подтверждения");
        }
        users.updateTotpSecret(user.id(), null);
        audit.log("TOTP_DISABLED", "USER", user.id(), Map.of());
    }

    @Transactional
    public void changePassword(String username, String current, String next) {
        var user = users.findByUsername(username).orElseThrow();
        if (!encoder.matches(current, user.passwordHash())) {
            throw new ValidationException("Текущий пароль указан неверно");
        }
        PasswordPolicy.check(next, username);
        users.updatePassword(user.id(), encoder.encode(next));
        audit.log("PASSWORD_CHANGED", "USER", user.id(), Map.of());
    }
}
