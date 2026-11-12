package io.github.yangorbunov.incidents.common;

/** Нарушение бизнес-правила или конкурентное изменение (оптимистичная блокировка). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException staleVersion(String entity) {
        return new ConflictException(entity + " изменён(а) другим пользователем. Обновите страницу и повторите.");
    }
}
