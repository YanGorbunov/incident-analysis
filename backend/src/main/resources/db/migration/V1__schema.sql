-- =====================================================================
-- Компонент учёта и анализа инцидентов — схема БД.
-- Основа: ВКР (Рис. 3.4, Листинг Б.1), с исправлениями — см. docs/RETRO.md.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- Нормативно-справочная информация
-- ---------------------------------------------------------------------

-- Справочник «Отдел» (Табл. 2.4). Код: XX
CREATE TABLE department (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(2)   NOT NULL UNIQUE CHECK (code ~ '^\d{2}$'),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    location    TEXT
);

-- Справочник «Должность» (Табл. 2.5). Код: ОО.XX, где ОО — код отдела.
-- В ВКР связи должности с отделом не было, хотя код её подразумевает.
CREATE TABLE post (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(5)   NOT NULL UNIQUE CHECK (code ~ '^\d{2}\.\d{2}$'),
    department_id BIGINT       NOT NULL REFERENCES department (id),
    name          VARCHAR(255) NOT NULL,
    description   TEXT
);
CREATE INDEX post_department_idx ON post (department_id);

-- Справочник «Приоритет» (Табл. 2.6) + целевые сроки SLA (в ВКР SLA
-- упоминается, но в модели отсутствует).
CREATE TABLE priority (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(2)   NOT NULL UNIQUE CHECK (code ~ '^\d{2}$'),
    name                   VARCHAR(255) NOT NULL UNIQUE,
    description            TEXT,
    sort_order             INT          NOT NULL DEFAULT 0,
    color                  VARCHAR(7)   NOT NULL DEFAULT '#888888' CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    response_sla_minutes   INT CHECK (response_sla_minutes > 0),
    resolution_sla_minutes INT CHECK (resolution_sla_minutes > 0)
);

-- Справочник «Статус» (Табл. 2.7). phase — машиночитаемая стадия:
-- аналитика не должна зависеть от названий статусов.
CREATE TABLE status (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(2)   NOT NULL UNIQUE CHECK (code ~ '^\d{2}$'),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    sort_order  INT          NOT NULL DEFAULT 0,
    phase       VARCHAR(16)  NOT NULL CHECK (phase IN ('NEW', 'ACTIVE', 'RESOLVED', 'CLOSED'))
);

-- Ведомство. Код: XXX
CREATE TABLE institution (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(3)   NOT NULL UNIQUE CHECK (code ~ '^\d{3}$'),
    name         VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) CHECK (phone_number ~ '^\+?[1-9]\d{1,14}$'),
    address      VARCHAR(500)
);

-- Тип конфигурационной единицы. Код: XXX
CREATE TABLE ci_type (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(3)   NOT NULL UNIQUE CHECK (code ~ '^\d{3}$'),
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Программное обеспечение. Код: XXXXX
CREATE TABLE software (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(5)   NOT NULL UNIQUE CHECK (code ~ '^\d{5}$'),
    name        VARCHAR(255) NOT NULL,
    version     VARCHAR(100),
    vendor      VARCHAR(255),
    license_key VARCHAR(255),
    UNIQUE (name, version)
);

-- ---------------------------------------------------------------------
-- Сотрудники и пользователи.
-- В ВКР это одна сущность: логин/пароль у каждого ответственного.
-- На деле ответственные приходят из трекера, а входят в систему только
-- аналитики — поэтому учётная запись вынесена отдельно.
-- ---------------------------------------------------------------------

-- Сотрудник. Код: ДДДД.XXXX, где ДДДД — код должности без точки.
-- department_id из ВКР убран: отдел однозначно определяется должностью.
CREATE TABLE employee (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(9)  NOT NULL UNIQUE CHECK (code ~ '^\d{4}\.\d{4}$'),
    external_id VARCHAR(64) UNIQUE,
    last_name   VARCHAR(100) NOT NULL,
    first_name  VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    email       VARCHAR(255),
    post_id     BIGINT      NOT NULL REFERENCES post (id),
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE INDEX employee_post_idx ON employee (post_id);

CREATE TABLE app_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    -- {bcrypt}$2a$... — DelegatingPasswordEncoder. В ВКР: password VARCHAR(45).
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'ANALYST', 'VIEWER')),
    employee_id     BIGINT UNIQUE REFERENCES employee (id),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    totp_secret     VARCHAR(64),
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Конфигурационные единицы. Код: ВВВ.XXXX, где ВВВ — код ведомства.
-- ---------------------------------------------------------------------
CREATE TABLE configuration_item (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(8)   NOT NULL UNIQUE CHECK (code ~ '^\d{3}\.\d{4}$'),
    external_id     VARCHAR(64) UNIQUE,
    name            VARCHAR(255) NOT NULL,
    ci_type_id      BIGINT       NOT NULL REFERENCES ci_type (id),
    institution_id  BIGINT       NOT NULL REFERENCES institution (id),
    ip_address      INET,
    technical_specs TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         INT          NOT NULL DEFAULT 0
);
CREATE INDEX ci_type_idx ON configuration_item (ci_type_id);
CREATE INDEX ci_institution_idx ON configuration_item (institution_id);

CREATE TABLE ci_software (
    ci_id       BIGINT NOT NULL REFERENCES configuration_item (id) ON DELETE CASCADE,
    software_id BIGINT NOT NULL REFERENCES software (id),
    PRIMARY KEY (ci_id, software_id)
);
CREATE INDEX ci_software_software_idx ON ci_software (software_id);

-- ---------------------------------------------------------------------
-- Инцидент. Код: ГГММДД.XXXX (дата регистрации + номер за сутки).
-- ---------------------------------------------------------------------
CREATE TABLE incident (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code              VARCHAR(11)  NOT NULL UNIQUE CHECK (code ~ '^\d{6}\.\d{4}$'),
    external_id       VARCHAR(64) UNIQUE,
    source            VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'IMPORT')),
    title             VARCHAR(500) NOT NULL,
    -- В ВКР VARCHAR(255), но сюда попадают логи и системные журналы.
    description       TEXT         NOT NULL,
    resolution        TEXT,
    registered_at     TIMESTAMPTZ  NOT NULL,
    -- Моменты первой реакции (переход в ACTIVE), решения (RESOLVED) и
    -- закрытия (CLOSED). SLA решения считается до resolved_at: ожидание
    -- подтверждения пользователя не вина исполнителя.
    first_response_at TIMESTAMPTZ,
    resolved_at       TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    priority_id       BIGINT       NOT NULL REFERENCES priority (id),
    status_id         BIGINT       NOT NULL REFERENCES status (id),
    -- Новый инцидент может быть ещё не назначен (статус «Новый»).
    assignee_id       BIGINT REFERENCES employee (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(64),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        VARCHAR(64),
    version           INT          NOT NULL DEFAULT 0,
    search            TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('russian', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('russian', coalesce(description, '')), 'B') ||
        setweight(to_tsvector('russian', coalesce(resolution, '')), 'C')
    ) STORED,
    -- CHECK (registered_at <= NOW()) из ВКР убран: недетерминированная
    -- функция в CHECK ломает восстановление дампов. Проверяется в приложении.
    CONSTRAINT incident_closed_after_registered CHECK (closed_at IS NULL OR closed_at >= registered_at),
    CONSTRAINT incident_response_after_registered CHECK (first_response_at IS NULL OR first_response_at >= registered_at),
    CONSTRAINT incident_resolved_after_registered CHECK (resolved_at IS NULL OR resolved_at >= registered_at)
);
CREATE INDEX incident_registered_idx ON incident (registered_at DESC);
CREATE INDEX incident_status_idx ON incident (status_id);
CREATE INDEX incident_priority_idx ON incident (priority_id);
CREATE INDEX incident_assignee_idx ON incident (assignee_id);
CREATE INDEX incident_search_idx ON incident USING GIN (search);
CREATE INDEX incident_title_trgm_idx ON incident USING GIN (title gin_trgm_ops);

CREATE TABLE incident_ci (
    incident_id BIGINT NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    ci_id       BIGINT NOT NULL REFERENCES configuration_item (id),
    PRIMARY KEY (incident_id, ci_id)
);
CREATE INDEX incident_ci_ci_idx ON incident_ci (ci_id);

-- История смены статусов: нужна для времени реакции и аудита.
CREATE TABLE incident_status_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id    BIGINT      NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    from_status_id BIGINT REFERENCES status (id),
    to_status_id   BIGINT      NOT NULL REFERENCES status (id),
    changed_at     TIMESTAMPTZ NOT NULL,
    changed_by     VARCHAR(64)
);
CREATE INDEX incident_status_history_incident_idx ON incident_status_history (incident_id, changed_at);

-- ---------------------------------------------------------------------
-- Категории инцидентов.
-- В ВКР категория = (id, name, дата), а параметры фильтра, по которым
-- аналитик её выделяет (Рис. 3.12), нигде не хранились.
-- Теперь: criteria — сам фильтр, category_incident — зафиксированный
-- срез на момент snapshot_at (отчёт воспроизводим).
-- ---------------------------------------------------------------------
CREATE TABLE category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(3)   NOT NULL UNIQUE CHECK (code ~ '^\d{3}$'),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    criteria    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(64),
    snapshot_at TIMESTAMPTZ
);

CREATE TABLE category_incident (
    category_id BIGINT NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    incident_id BIGINT NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    PRIMARY KEY (category_id, incident_id)
);
CREATE INDEX category_incident_incident_idx ON category_incident (incident_id);

-- ---------------------------------------------------------------------
-- Служебные таблицы
-- ---------------------------------------------------------------------

-- Счётчики для кодов вида PREFIX.XXXX (атомарный upsert, без гонок).
CREATE TABLE code_counter (
    scope      VARCHAR(32) NOT NULL,
    prefix     VARCHAR(16) NOT NULL,
    last_value INT         NOT NULL,
    PRIMARY KEY (scope, prefix)
);

CREATE TABLE import_run (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind         VARCHAR(16)  NOT NULL CHECK (kind IN ('INCIDENT', 'CI')),
    external_id  VARCHAR(64)  NOT NULL,
    outcome      VARCHAR(16)  NOT NULL CHECK (outcome IN ('CREATED', 'UPDATED', 'NOT_FOUND', 'FAILED')),
    message      TEXT,
    entity_id    BIGINT,
    requested_by VARCHAR(64),
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    duration_ms  BIGINT
);
CREATE INDEX import_run_started_idx ON import_run (started_at DESC);

CREATE TABLE audit_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    username    VARCHAR(64),
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id   VARCHAR(64),
    details     JSONB,
    remote_addr VARCHAR(64)
);
CREATE INDEX audit_event_occurred_idx ON audit_event (occurred_at DESC);
CREATE INDEX audit_event_entity_idx ON audit_event (entity_type, entity_id);
