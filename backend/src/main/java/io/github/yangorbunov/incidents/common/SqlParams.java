package io.github.yangorbunov.incidents.common;

import java.lang.reflect.RecordComponent;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Record → именованные параметры. Нужен потому, что в JdbcClient
 * {@code paramSource(obj)} заменяет параметры, заданные через {@code param()},
 * а не дополняет их. Enum передаётся как name().
 */
public final class SqlParams {

    private SqlParams() {
    }

    public static MapSqlParameterSource of(Record record) {
        var source = new MapSqlParameterSource();
        for (RecordComponent rc : record.getClass().getRecordComponents()) {
            try {
                Object value = rc.getAccessor().invoke(record);
                source.addValue(rc.getName(), value instanceof Enum<?> e ? e.name() : value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return source;
    }
}
