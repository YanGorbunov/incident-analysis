-- Демонстрационные данные (профиль demo). Идемпотентно: выполняется,
-- только если инцидентов ещё нет. Генерация детерминирована (setseed).

DO $$
DECLARE
    v_zone      CONSTANT TEXT := 'Europe/Moscow';
    v_start     TIMESTAMPTZ := date_trunc('day', now()) - INTERVAL '180 days';
    v_titles    TEXT[] := ARRAY[
        'Критическая ошибка после замены СКЗИ',
        'Не обрабатываются запросы на проверку в ЦБД',
        'Не удаётся аннулировать паспорт',
        'На печатной форме анкеты не отображаются сведения',
        'Сбой в работе серверного программного обеспечения',
        'КБК не видит ПКРБП МФЦ',
        'Проблемы при настройке СКЗИ',
        'Долгий ответ сервиса персонализации',
        'Ошибка синхронизации справочников',
        'Не формируется заявление в электронном виде',
        'Недоступен веб-интерфейс АРМ оператора',
        'Ошибка подписи пакета данных',
        'Переполнение очереди обмена сообщениями',
        'Не печатается бланк на принтере',
        'Запрос на предоставление выгрузки'];
    v_desc      TEXT[] := ARRAY[
        'Проблема зафиксирована системой мониторинга. Наблюдаются задержки в обработке анкет.',
        'Пользователь сообщил об ошибке по телефону. В журнале приложения: java.net.SocketTimeoutException: Read timed out',
        'Ошибка воспроизводится после последнего обновления. Код ошибки ERR-4021 в системном журнале.',
        'Сообщение поступило по электронной почте. Приложены лог-файлы сервера приложений.'];
    v_res       TEXT[] := ARRAY[
        'Выполнен откат обновления до предыдущей стабильной версии, сервер перезагружен.',
        'Переустановлен сертификат СКЗИ, проверена цепочка доверия.',
        'Увеличен таймаут соединения, очищена очередь сообщений.',
        'Исправлен шаблон печатной формы, выпущено обновление.',
        'Даны консультации пользователю, проблема на стороне рабочего места.'];
    v_code      TEXT;
    v_reg       TIMESTAMPTZ;
    v_prio      BIGINT;
    v_prio_code TEXT;
    v_status    TEXT;
    v_resp      TIMESTAMPTZ;
    v_closed    TIMESTAMPTZ;
    v_resolved  TIMESTAMPTZ;
    v_inc       BIGINT;
    v_assignee  BIGINT;
    v_roll      DOUBLE PRECISION;
    v_rs        INT;
    v_ls        INT;
    i           INT;
BEGIN
    IF EXISTS (SELECT 1 FROM incident) THEN
        RETURN;
    END IF;
    PERFORM setseed(0.42);

    INSERT INTO institution (code, name, phone_number, address) VALUES
    ('001', 'МРЦОД', '+74950000001', 'г. Москва'),
    ('002', 'ГУВМ МВД России', '+74950000002', 'г. Москва'),
    ('003', 'Консульский департамент МИД России', '+74950000003', 'г. Москва'),
    ('004', 'МФЦ Челябинской области', '+73510000004', 'г. Челябинск'),
    ('005', 'МФЦ Новосибирской области', '+73830000005', 'г. Новосибирск')
    ON CONFLICT (code) DO NOTHING;

    INSERT INTO software (code, name, version, vendor) VALUES
    ('00001', 'Red Hat Enterprise Linux', '8', 'Red Hat'),
    ('00002', 'Apache Tomcat', '10', 'Apache Software Foundation'),
    ('00003', 'PostgreSQL', '14', 'PostgreSQL Global Development Group'),
    ('00004', 'Astra Linux Special Edition', '1.7', 'Группа Астра'),
    ('00005', 'КриптоПро CSP', '5.0', 'КриптоПро'),
    ('00006', 'ViPNet Client', '4.5', 'ИнфоТеКС')
    ON CONFLICT (name, version) DO NOTHING;

    INSERT INTO code_counter (scope, prefix, last_value)
    SELECT 'INSTITUTION', '', max(code::int) FROM institution
    UNION ALL SELECT 'SOFTWARE', '', max(code::int) FROM software
    ON CONFLICT (scope, prefix) DO UPDATE SET last_value = EXCLUDED.last_value;

    -- Сотрудники (код ДДДД.XXXX)
    INSERT INTO employee (code, last_name, first_name, middle_name, post_id, external_id)
    SELECT replace(p.code, '.', '') || '.' || lpad(v.n::text, 4, '0'), v.ln, v.fn, v.mn, p.id, v.ext
    FROM (VALUES
        ('01.03', 1, 'Егоров', 'Дмитрий', 'Евгеньевич', 'u-egorov'),
        ('01.02', 1, 'Сергеев', 'Михаил', 'Дмитриевич', 'u-sergeev'),
        ('02.03', 1, 'Кузнецова', 'Анна', 'Сергеевна', 'u-kuznetsova'),
        ('02.02', 1, 'Орлов', 'Илья', 'Андреевич', 'u-orlov'),
        ('02.03', 2, 'Волков', 'Артём', 'Олегович', 'u-volkov'),
        ('03.03', 1, 'Петров', 'Павел', 'Викторович', 'u-petrov'),
        ('04.02', 1, 'Смирнова', 'Елена', 'Игоревна', 'u-smirnova'),
        ('04.03', 1, 'Фёдоров', 'Николай', 'Петрович', 'u-fedorov')
    ) AS v(post, n, ln, fn, mn, ext)
    JOIN post p ON p.code = v.post;

    INSERT INTO code_counter (scope, prefix, last_value)
    SELECT 'EMPLOYEE', split_part(code, '.', 1), max(split_part(code, '.', 2)::int)
    FROM employee GROUP BY 1, 2
    ON CONFLICT (scope, prefix) DO UPDATE SET last_value = EXCLUDED.last_value;

    -- Конфигурационные единицы (код ВВВ.XXXX)
    INSERT INTO configuration_item (code, external_id, name, ci_type_id, institution_id, ip_address, technical_specs)
    SELECT ins.code || '.' || lpad(v.n::text, 4, '0'), v.ext, v.name, t.id, ins.id, v.ip::inet, v.specs
    FROM (VALUES
        ('001', 1, 'ci-1001', 'Сервер МРЦОД', '001', '10.0.0.223',
         E'- Процессор: два ЦП Эльбрус-8С\n- ОЗУ: 16 ГБ DDR4\n- Накопитель: 2 × HDD 2 ТБ\n- Сеть: 2 × 10GbE'),
        ('001', 2, 'ci-1002', 'Сервер ЦБД', '001', '10.0.0.10', E'- ОЗУ: 256 ГБ\n- СХД: 40 ТБ'),
        ('001', 3, 'ci-1003', 'Шлюз межведомственного обмена', '006', '10.0.1.5', NULL),
        ('002', 1, 'ci-2001', 'АРМ оператора ГУВМ №1', '002', '10.2.0.11', NULL),
        ('002', 2, 'ci-2002', 'СКЗИ ГУВМ', '004', '10.2.0.2', NULL),
        ('003', 1, 'ci-3001', 'Сервер консульского департамента', '001', '10.3.0.4', NULL),
        ('003', 2, 'ci-3002', 'Маршрутизатор КД', '003', '10.3.0.1', NULL),
        ('004', 1, 'ci-4001', 'ПКРБП МФЦ Челябинск', '002', '10.4.0.21', NULL),
        ('004', 2, 'ci-4002', 'Принтер бланков МФЦ Челябинск', '005', '10.4.0.30', NULL),
        ('004', 3, 'ci-4003', 'СКЗИ МФЦ Челябинск', '004', '10.4.0.2', NULL),
        ('005', 1, 'ci-5001', 'ПКРБП МФЦ Новосибирск', '002', '10.5.0.21', NULL),
        ('005', 2, 'ci-5002', 'Коммутатор МФЦ Новосибирск', '003', '10.5.0.1', NULL)
    ) AS v(inst, n, ext, name, type, ip, specs)
    JOIN institution ins ON ins.code = v.inst
    JOIN ci_type t ON t.code = v.type;

    INSERT INTO code_counter (scope, prefix, last_value)
    SELECT 'CI', split_part(code, '.', 1), max(split_part(code, '.', 2)::int)
    FROM configuration_item GROUP BY 1, 2
    ON CONFLICT (scope, prefix) DO UPDATE SET last_value = EXCLUDED.last_value;

    INSERT INTO ci_software (ci_id, software_id)
    SELECT c.id, s.id FROM configuration_item c JOIN software s ON
        (c.ci_type_id = (SELECT id FROM ci_type WHERE code = '001') AND s.code IN ('00001', '00002', '00003'))
     OR (c.ci_type_id = (SELECT id FROM ci_type WHERE code = '002') AND s.code IN ('00004', '00006'))
     OR (c.ci_type_id = (SELECT id FROM ci_type WHERE code = '004') AND s.code = '00005');

    -- Инциденты: 260 штук за полгода, в хронологическом порядке,
    -- чтобы порядковые номера в коде шли по времени регистрации.
    i := 0;
    FOR v_reg IN
        SELECT date_trunc('minute', v_start + (random() * 180) * INTERVAL '1 day') AS ts
        FROM generate_series(1, 260) ORDER BY 1
    LOOP
        i := i + 1;
        v_roll := random();
        v_prio_code := CASE WHEN v_roll < 0.08 THEN '01' WHEN v_roll < 0.30 THEN '02'
                            WHEN v_roll < 0.75 THEN '03' WHEN v_roll < 0.93 THEN '04' ELSE '05' END;
        SELECT id INTO v_prio FROM priority WHERE code = v_prio_code;
        SELECT coalesce(response_sla_minutes, 1440), coalesce(resolution_sla_minutes, 7200)
          INTO v_rs, v_ls FROM priority WHERE id = v_prio;

        SELECT id INTO v_assignee FROM employee ORDER BY random() LIMIT 1;

        INSERT INTO code_counter (scope, prefix, last_value)
        VALUES ('INCIDENT', to_char(v_reg AT TIME ZONE v_zone, 'YYMMDD'), 1)
        ON CONFLICT (scope, prefix) DO UPDATE SET last_value = code_counter.last_value + 1
        RETURNING prefix || '.' || lpad(last_value::text, 4, '0') INTO v_code;
        -- ~80% укладываются в SLA реакции и решения
        v_resp := v_reg + (power(random(), 2) * v_rs * 1.6) * INTERVAL '1 minute';
        v_resolved := v_reg + (power(random(), 1.7) * v_ls * 1.35) * INTERVAL '1 minute';
        IF v_resolved < v_resp THEN v_resolved := v_resp + INTERVAL '30 minutes'; END IF;
        v_closed := v_resolved + (random() * 0.8) * INTERVAL '1 day';

        -- Свежие инциденты чаще открыты
        v_roll := random() * least(1.0, extract(epoch FROM now() - v_reg) / 86400 / 30);
        v_status := CASE WHEN v_closed > now() OR v_roll < 0.05 THEN
                              (ARRAY['01', '02', '03', '04', '05'])[1 + floor(random() * 5)::int]
                         ELSE '06' END;
        IF v_status IN ('01', '02') THEN
            v_resp := NULL; v_assignee := CASE WHEN v_status = '01' THEN NULL ELSE v_assignee END;
        END IF;
        IF v_resp IS NOT NULL AND v_resp > now() THEN v_resp := now() - INTERVAL '1 hour'; END IF;
        IF v_resp IS NOT NULL AND v_resp < v_reg THEN v_resp := v_reg; END IF;

        INSERT INTO incident (code, external_id, source, title, description, resolution, registered_at,
                              first_response_at, resolved_at, closed_at, priority_id, status_id, assignee_id,
                              created_by, updated_by)
        VALUES (v_code, 'TASK-' || (100000 + i), 'IMPORT',
                v_titles[1 + floor(random() * array_length(v_titles, 1))::int],
                v_desc[1 + floor(random() * array_length(v_desc, 1))::int],
                CASE WHEN v_status IN ('05', '06') THEN v_res[1 + floor(random() * array_length(v_res, 1))::int] END,
                v_reg, v_resp,
                CASE WHEN v_status IN ('05', '06') THEN least(v_resolved, now()) END,
                CASE WHEN v_status = '06' THEN v_closed END,
                v_prio, (SELECT id FROM status WHERE code = v_status), v_assignee,
                'demo', 'demo')
        RETURNING id INTO v_inc;

        INSERT INTO incident_ci (incident_id, ci_id)
        SELECT v_inc, id FROM configuration_item ORDER BY random() LIMIT (1 + floor(random() * 2)::int);

        -- История статусов, согласованная с итоговым статусом
        INSERT INTO incident_status_history (incident_id, from_status_id, to_status_id, changed_at, changed_by)
        SELECT v_inc, lag(s.id) OVER (ORDER BY h.ord), s.id, h.ts, 'demo'
        FROM (VALUES
            (1, '01', v_reg),
            (2, '03', v_resp),
            (3, '04', v_resp + INTERVAL '5 minutes'),
            (4, '05', CASE WHEN v_status IN ('05', '06') THEN least(v_resolved, now()) END),
            (5, '06', CASE WHEN v_status = '06' THEN v_closed END)
        ) AS h(ord, code, ts)
        JOIN status s ON s.code = h.code
        WHERE h.ts IS NOT NULL
          AND (h.ord = 1 OR v_status NOT IN ('01', '02'))
          AND h.ord <= CASE v_status WHEN '03' THEN 2 WHEN '04' THEN 3 WHEN '05' THEN 4 WHEN '06' THEN 5 ELSE 1 END;

        IF v_status = '02' THEN
            INSERT INTO incident_status_history (incident_id, from_status_id, to_status_id, changed_at, changed_by)
            SELECT v_inc, (SELECT id FROM status WHERE code = '01'), id, v_reg + INTERVAL '10 minutes', 'demo'
            FROM status WHERE code = '02';
        END IF;
    END LOOP;

END
$$;
