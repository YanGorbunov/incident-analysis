package io.github.yangorbunov.incidents.common;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Ошибки API в формате RFC 9457 (application/problem+json). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validation(ValidationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(),
                        f -> f.getDefaultMessage() == null ? "некорректное значение" : f.getDefaultMessage(),
                        (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Проверьте заполнение полей");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        log.info("Integrity violation: {}", e.getMostSpecificCause().getMessage());
        String detail = switch (constraintKind(e)) {
            case "unique" -> "Запись с такими значениями уже существует";
            case "foreign" -> "Запись используется в других данных или ссылается на несуществующую";
            default -> "Данные нарушают ограничения целостности";
        };
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }

    private static String constraintKind(DataIntegrityViolationException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase();
        if (msg.contains("unique") || msg.contains("duplicate")) {
            return "unique";
        }
        if (msg.contains("foreign key")) {
            return "foreign";
        }
        return "other";
    }
}
