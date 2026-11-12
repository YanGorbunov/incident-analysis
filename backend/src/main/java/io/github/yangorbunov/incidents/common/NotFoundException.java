package io.github.yangorbunov.incidents.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super("%s %s не найден(а)".formatted(entity, id));
    }
}
