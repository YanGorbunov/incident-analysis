# Incident Analytics — компонент учёта и анализа инцидентов

Реализация выпускной квалификационной работы (РТУ МИРЭА, 2024): компонент учёта и анализа
инцидентов системы управления инцидентами. В ВКР было только проектирование; здесь — работающее
приложение, сделанное «как в дипломе, но правильнее» спустя два года коммерческой разработки.

**Что изменено относительно проекта и почему — [docs/RETRO.md](docs/RETRO.md)**, ключевые решения — [docs/adr](docs/adr).

## Возможности

- **Инциденты:** список с поиском (полнотекстовый, с морфологией русского), сортировкой, выбором колонок,
  фильтрами; карточка инцидента; импорт по ID из системы управления задачами; история статусов.
- **Конфигурационные единицы:** учёт, связь с ведомством и ПО, импорт из трекера.
- **Справочники:** статусы (с фазами), приоритеты (с целевыми сроками SLA), ведомства, типы КЕ, ПО,
  сотрудники, отделы, должности. Коды объектов — по системе кодирования ВКР (Табл. 2.3).
- **Категории инцидентов:** сохранённый фильтр + зафиксированный срез.
- **Отчётность:** сводный отчёт по макету ВКР (формулы 2.1, 2.2) + медиана, p90, время реакции,
  соблюдение SLA, разрезы по ведомствам/КЕ/ответственным, понедельная динамика. Экспорт DOCX и XLSX.
- **Безопасность:** роли ADMIN / ANALYST / VIEWER, BCrypt, блокировка после неудачных попыток, TOTP,
  журнал событий, CSRF, CSP.
- **Эксплуатация:** Flyway, Actuator + Prometheus, резервное копирование по расписанию, CI.

## Стек

| | |
|---|---|
| Backend | Java 25, Spring Boot 4.1 (WebMVC, Security, JDBC/JdbcClient, Validation, Actuator), Flyway, Apache POI |
| БД | PostgreSQL 17 (FTS, JSONB, INET, `percentile_cont`) |
| Frontend | React 19, TypeScript, Vite, TanStack Query, React Router, Recharts |
| Тесты | JUnit 5, Testcontainers (PostgreSQL) |
| Инфраструктура | Docker, docker-compose, GitHub Actions |

## Быстрый старт

### Всё в Docker
```bash
docker compose up -d --build
# http://localhost:8080   admin / admin   (профиль demo: 260 инцидентов за полгода)
```
Демо-пользователи: `petrov` / `analyst-demo-pass` (аналитик Петров П. В. из макетов),
`viewer` / `viewer-demo-pass` (только просмотр).

### Разработка
```bash
docker compose up -d postgres

cd backend
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun     # http://localhost:8080/api

cd ../frontend
npm install
npm run dev                                       # http://localhost:5173 (проксирует /api на 8080)
```

### Тесты
```bash
cd backend && ./gradlew test      # нужен Docker для Testcontainers
cd frontend && npm run typecheck
```

### Импорт: что попробовать
Встроенные тестовые данные трекера (`backend/src/main/resources/tracker/fixtures.json`):
инциденты `131222846` (ID с макета Рис. 3.6), `TASK-300001`, `TASK-300002` (подтянет
отсутствующую КЕ), `TASK-300003` (неизвестный ответственный → предупреждение); КЕ `ci-9001`.
Реальный трекер: `app.tracker.mode=http`, `TRACKER_URL`, `TRACKER_TOKEN`, маппинг статусов
и приоритетов — в `application.yml`.

## Структура

```
backend/
  src/main/java/io/github/yangorbunov/incidents/
    incident/    инциденты, критерии поиска (IncidentSql — общий SQL для списка, категорий, отчётов)
    category/    категории: фильтр + срез
    report/      аналитика (AnalyticsRepository), экспорт DOCX/XLSX
    tracker/     импорт из системы управления задачами
    ci/          конфигурационные единицы
    reference/   справочники
    security/    вход, TOTP, пользователи, роли
    audit/       журнал событий
  src/main/resources/db/migration   схема и НСИ из ВКР
  src/main/resources/db/demo        демо-данные (профиль demo)
frontend/src/
  pages/         экраны по макетам ВКР (Рис. 3.5–3.13) + админка
  components/    таблица инцидентов, форма критериев, UI
docs/
  RETRO.md       ретроспектива: ВКР → реализация
  adr/           архитектурные решения
```

## Соответствие ВКР

| ВКР | Реализация |
|-----|------------|
| Рис. 2.5, 3.4, Листинг Б.1 — модели БД | `db/migration/V1__schema.sql` (с исправлениями, см. RETRO §2) |
| Табл. 2.3 — классификация и кодирование | `CodeGenerator`, ADR-0004 |
| Табл. 2.4–2.7 — справочники | `db/migration/V2__reference_data.sql` |
| Формулы 2.1, 2.2 | `AnalyticsRepository.summary`, тест `CategoryReportIT` |
| Рис. 2.6 — сводный отчёт | `ReportsPage`, `DocxReportWriter`, `XlsxReportWriter` |
| Рис. 3.1 — дерево функций | разделы меню и API |
| Рис. 3.3 — структурная схема (модули) | пакеты backend; резервное копирование — сервис `backup` (ADR-0005) |
| Рис. 3.5–3.13 — экранные формы | `frontend/src/pages/*` |
| п. 3.4 — информационная безопасность | `security/*`, `audit/*` |
