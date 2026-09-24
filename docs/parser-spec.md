# Спецификация парсера мероприятий

**Проект:** JOIN  
**Версия:** 1.0  
**Дата:** 2026-03-21  

---

## 1. Обзор

Парсер собирает мероприятия из внешних источников, нормализует данные и сохраняет в таблицу `events` проекта JOIN. Цель — автоматическое наполнение афиши актуальными мероприятиями без ручного ввода.

---

## 2. Текущая модель мероприятий (Event)

### 2.1 Таблица `events`

| Поле | Тип | Обязательное | Описание |
|------|-----|:---:|----------|
| `id` | BIGINT (auto) | ✅ | PK |
| `title` | VARCHAR(255) | ✅ | Название |
| `description` | TEXT | — | Описание |
| `type` | VARCHAR(50) / enum | ✅ | Категория (EventType) |
| `image_url` | VARCHAR(512) | — | URL изображения |
| `price` | DECIMAL(10,2) | — | Цена (null = не указана, 0 = бесплатно) |
| `event_date` | DATE | ✅ | Дата проведения |
| `event_time` | TIME | — | Время начала |
| `ticket_url` | VARCHAR(512) | — | Ссылка на покупку билета |
| `city` | VARCHAR(255) | ✅ | Город |
| `created_at` | TIMESTAMP | ✅ | Дата создания записи |

### 2.2 Enum `EventType`

```
CAREER, THEATER, ART, MUSIC, SPORT, CINEMA, MASTER_CLASS, EXCURSION, FESTIVAL
```

---

## 3. Источники данных

### 3.1 KudaGo API (публичный, без ключа)

| Параметр | Значение |
|----------|----------|
| **Базовый URL** | `https://kudago.com/public-api/v1.4` |
| **Endpoint событий** | `GET /events/` |
| **Endpoint категорий** | `GET /event-categories/` |
| **Авторизация** | Не требуется |
| **Rate limit** | Не документирован; рекомендуется ≤ 2 req/s |
| **Пагинация** | `page` + `page_size` (max 100) |
| **Формат** | JSON |

**Параметры запроса событий:**

```
GET /events/?fields=id,title,description,categories,dates,price,images,location,place,site_url
  &location={city_slug}
  &actual_since={unix_timestamp}
  &page_size=100
  &page={n}
  &text_format=plain
  &expand=place
```

**Города (location slug):** `msk` (Москва), `spb` (Санкт-Петербург), `nsk` (Новосибирск), `ekb` (Екатеринбург), `nnv` (Нижний Новгород), `kzn` (Казань), `smr` (Самара), `krd` (Краснодар), `sochi` (Сочи), `ufa` (Уфа), `krasnoyarsk` (Красноярск).

**Структура ответа:**

```json
{
  "count": 92680,
  "next": "https://kudago.com/public-api/v1.4/events/?page=2",
  "results": [
    {
      "id": 154041,
      "title": "Название",
      "description": "Текстовое описание",
      "categories": ["concert", "festival"],
      "dates": [{ "start": 1497078000, "end": 1497297600 }],
      "price": "от 500 руб.",
      "images": [{ "image": "https://media.kudago.com/..." }],
      "location": { "slug": "msk" },
      "place": { "id": 15022 },
      "site_url": "https://kudago.com/msk/event/..."
    }
  ]
}
```

**Категории KudaGo** (получены из API):

| KudaGo slug | KudaGo name |
|-------------|-------------|
| `business-events` | Business events |
| `cinema` | Movies |
| `concert` | Concerts |
| `education` | Education |
| `entertainment` | Entertainment |
| `exhibition` | Exhibitions |
| `fashion` | Fashion |
| `festival` | Festivals |
| `holiday` | Holidays |
| `kids` | Kids |
| `other` | Other |
| `party` | Parties |
| `photo` | Photoevents |
| `quest` | Quests |
| `recreation` | Recreation |
| `shopping` | Shopping |
| `social-activity` | Charity |
| `stock` | Promotions |
| `theater` | Theater |
| `tour` | Tours |
| `yarmarki-razvlecheniya-yarmarki` | Fair |

---

### 3.2 Timepad API (требует токен)

| Параметр | Значение |
|----------|----------|
| **Базовый URL** | `https://api.timepad.ru/v1` |
| **Endpoint событий** | `GET /events` |
| **Авторизация** | Bearer token (`Authorization: Bearer {TOKEN}`) |
| **Rate limit** | Документирован; рекомендуется ≤ 5 req/s |
| **Пагинация** | `skip` + `limit` (max 100) |
| **Формат** | JSON |

> ⚠️ **Требуется:** зарегистрировать приложение на https://dev.timepad.ru и получить OAuth2 токен. Хранить в `application.yml` как `parser.timepad.token`.

**Параметры запроса:**

```
GET /events?limit=100&skip=0
  &starts_at_min={ISO_8601}
  &cities={city_name}
  &fields=id,name,description_short,starts_at,ends_at,url,poster_image,location,min_price,max_price,categories
```

**Структура ответа (ожидаемая):**

```json
{
  "total": 5000,
  "values": [
    {
      "id": 123456,
      "name": "Название",
      "description_short": "Краткое описание",
      "starts_at": "2026-04-15T19:00:00+03:00",
      "ends_at": "2026-04-15T22:00:00+03:00",
      "url": "https://event.timepad.ru/...",
      "poster_image": { "default_url": "https://..." },
      "location": { "city": "Москва", "address": "..." },
      "min_price": 0,
      "max_price": 2500,
      "categories": [{ "id": 452, "name": "Бизнес" }]
    }
  ]
}
```

---

### 3.3 Telegram-каналы

| Параметр | Значение |
|----------|----------|
| **Механизм** | Telegram Bot API / MTProto (Telethon/Pyrogram) |
| **Авторизация** | Bot Token или User Session |
| **Формат** | Текст сообщений (парсинг NLP / regex) |

**Рекомендуемые каналы:**

| Канал | Тематика | Город |
|-------|----------|-------|
| `@afaboroda` | Афиша Москвы (концерты, выставки, театр) | Москва |
| `@kuda_msk` | Куда сходить в Москве | Москва |
| `@afisha_msk` | Московская афиша | Москва |
| `@spb_afisha` | Афиша Петербурга | СПб |
| `@kuda_spb` | Куда сходить в Питере | СПб |
| `@free_events_msk` | Бесплатные мероприятия | Москва |
| `@concert_moscow` | Концерты Москвы | Москва |

> ⚠️ **Важно:** перед подключением каждого канала проверить его актуальность и корректность username. Каналы могут закрываться или менять адрес. Список должен обновляться через конфигурацию.

**Стратегия парсинга Telegram:**

1. Бот подписывается на каналы (или использует `getUpdates` / `getChatHistory`)
2. Читает последние N сообщений за сутки
3. Извлекает структурированные данные через:
   - Regex-паттерны (дата, время, цена, адрес)
   - Ключевые слова для определения категории
   - Ссылки на билеты/события
4. Результат — кандидат на мероприятие со статусом `NEEDS_REVIEW`

**Ограничения:**
- Низкая надёжность извлечения полей (неструктурированный текст)
- Рекомендуется модерация через админку перед публикацией
- Приоритет: Phase 2 (после стабилизации KudaGo + Timepad)

---

### 3.4 Дополнительные источники

#### 3.4.1 Afisha.ru (scraping)

| Параметр | Значение |
|----------|----------|
| **URL** | `https://www.afisha.ru/msk/schedule_cinema/` и аналогичные |
| **Механизм** | HTML scraping (Jsoup) |
| **Авторизация** | Не требуется |

> ⚠️ Scraping — ненадёжный метод. Возможны блокировки, изменения вёрстки. Реализовывать как опциональный источник с fallback.

#### 3.4.2 culture.ru (Портал культурного наследия)

| Параметр | Значение |
|----------|----------|
| **API URL** | `https://all.culture.ru/api/2.0/events` |
| **Авторизация** | API key (бесплатный, по заявке) |
| **Формат** | JSON |

**Параметры запроса:**

```
GET /api/2.0/events?limit=100&offset=0&start={ISO_date}&status=accepted&locale={city_id}
```

> Хороший источник для культурных мероприятий (выставки, театр, концерты). Покрывает всю Россию.

#### 3.4.3 Kassir.ru (scraping, опционально)

Рассмотреть в будущем как источник для билетных мероприятий. Требует reverse engineering или партнёрское соглашение.

---

## 4. Маппинг полей

### 4.1 KudaGo → Event

| Поле Event | Источник KudaGo | Трансформация |
|------------|----------------|---------------|
| `title` | `title` | Прямое (truncate 255 символов) |
| `description` | `description` | Strip HTML тегов (или использовать `text_format=plain`) |
| `type` | `categories[0]` | Маппинг через таблицу категорий (§4.4) |
| `image_url` | `images[0].image` | Прямое (URL) |
| `price` | `price` | Парсинг строки: извлечь число, "бесплатно" → 0, пустая → null |
| `event_date` | `dates[0].start` | Unix timestamp → LocalDate (Moscow TZ) |
| `event_time` | `dates[0].start` | Unix timestamp → LocalTime (Moscow TZ) |
| `ticket_url` | `site_url` | Прямое (URL страницы на KudaGo) |
| `city` | `location.slug` | Маппинг slug → название города (§4.5) |
| `created_at` | — | `LocalDateTime.now()` при вставке |

### 4.2 Timepad → Event

| Поле Event | Источник Timepad | Трансформация |
|------------|-----------------|---------------|
| `title` | `name` | Прямое (truncate 255) |
| `description` | `description_short` | Прямое |
| `type` | `categories[0].name` | Маппинг через таблицу категорий (§4.4) |
| `image_url` | `poster_image.default_url` | Прямое (URL) |
| `price` | `min_price` | Число; 0 = бесплатно |
| `event_date` | `starts_at` | ISO 8601 → LocalDate |
| `event_time` | `starts_at` | ISO 8601 → LocalTime |
| `ticket_url` | `url` | Прямое |
| `city` | `location.city` | Прямое (нормализовать регистр) |
| `created_at` | — | `LocalDateTime.now()` |

### 4.3 Telegram → Event

| Поле Event | Источник | Трансформация |
|------------|----------|---------------|
| `title` | Первая строка / заголовок | Regex, первые 255 символов |
| `description` | Тело сообщения | Полный текст |
| `type` | Ключевые слова | NLP/keyword маппинг (§4.4) |
| `image_url` | Фото из сообщения | URL через Telegram File API |
| `price` | Текст | Regex: `\d+\s*(руб|₽|р\.)` → число |
| `event_date` | Текст | Regex: даты в формате DD.MM, DD/MM/YYYY и т.д. |
| `event_time` | Текст | Regex: `\d{1,2}:\d{2}` |
| `ticket_url` | Ссылки в тексте | Первая HTTP(S) ссылка |
| `city` | Метаданные канала | Из конфигурации канала |
| `created_at` | — | `LocalDateTime.now()` |

### 4.4 Маппинг категорий → EventType

| Внешняя категория | EventType | Источник |
|-------------------|-----------|----------|
| `concert`, `Концерты`, `Музыка`, `party` | **MUSIC** | KudaGo, Timepad |
| `theater`, `Театр`, `Спектакли`, `Балет`, `Опера` | **THEATER** | KudaGo, Timepad |
| `exhibition`, `Выставки`, `Искусство`, `photo` | **ART** | KudaGo, Timepad |
| `cinema`, `Кино`, `Фильмы` | **CINEMA** | KudaGo, Timepad |
| `festival`, `Фестивали`, `holiday`, `yarmarki-razvlecheniya-yarmarki` | **FESTIVAL** | KudaGo, Timepad |
| `education`, `Мастер-класс`, `Тренинг`, `Воркшоп`, `quest` | **MASTER_CLASS** | KudaGo, Timepad |
| `tour`, `Экскурсии`, `recreation` | **EXCURSION** | KudaGo, Timepad |
| `business-events`, `Бизнес`, `Нетворкинг`, `Конференция`, `Карьера` | **CAREER** | KudaGo, Timepad |
| `entertainment`, `Спорт`, `Марафон`, `Забег` | **SPORT** | KudaGo, Timepad |
| Не распознано / `other`, `fashion`, `shopping`, `stock`, `social-activity`, `kids` | **FESTIVAL** (fallback) | Все |

> **Правило:** если у события несколько категорий, выбирать первую, которая маппится не на fallback. Если все маппятся на fallback — использовать fallback.

> **Конфигурация:** маппинг хранить в `application.yml` или отдельном `category-mapping.yml` для гибкого обновления без пересборки.

### 4.5 Маппинг городов KudaGo

| KudaGo slug | Город |
|-------------|-------|
| `msk` | Москва |
| `spb` | Санкт-Петербург |
| `nsk` | Новосибирск |
| `ekb` | Екатеринбург |
| `nnv` | Нижний Новгород |
| `kzn` | Казань |
| `smr` | Самара |
| `krd` | Краснодар |
| `sochi` | Сочи |
| `ufa` | Уфа |
| `krasnoyarsk` | Красноярск |

---

## 5. Схема расширения БД

### 5.1 Новые поля в таблице `events` (миграция)

```yaml
# Liquibase changeset: add-parser-fields-to-events
databaseChangeLog:
  - changeSet:
      id: 0XX-add-parser-fields
      author: join-team
      changes:
        - addColumn:
            tableName: events
            columns:
              - column:
                  name: source
                  type: VARCHAR(50)
                  remarks: "Источник: MANUAL, KUDAGO, TIMEPAD, TELEGRAM, CULTURE_RU"
              - column:
                  name: external_id
                  type: VARCHAR(255)
                  remarks: "ID мероприятия во внешней системе"
              - column:
                  name: status
                  type: VARCHAR(20)
                  defaultValue: "ACTIVE"
                  remarks: "ACTIVE, NEEDS_REVIEW, REJECTED, ARCHIVED"
              - column:
                  name: updated_at
                  type: TIMESTAMP
        - addUniqueConstraint:
            tableName: events
            columnNames: source, external_id
            constraintName: uq_events_source_external_id
```

### 5.2 Таблица `parser_runs` (логирование запусков)

```yaml
# Liquibase changeset: create-parser-runs-table
databaseChangeLog:
  - changeSet:
      id: 0XX-create-parser-runs
      author: join-team
      changes:
        - createTable:
            tableName: parser_runs
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
              - column:
                  name: source
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: started_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: finished_at
                  type: TIMESTAMP
              - column:
                  name: status
                  type: VARCHAR(20)
                  remarks: "RUNNING, SUCCESS, PARTIAL, FAILED"
              - column:
                  name: events_found
                  type: INT
                  defaultValueNumeric: 0
              - column:
                  name: events_created
                  type: INT
                  defaultValueNumeric: 0
              - column:
                  name: events_updated
                  type: INT
                  defaultValueNumeric: 0
              - column:
                  name: events_skipped
                  type: INT
                  defaultValueNumeric: 0
              - column:
                  name: error_message
                  type: TEXT
              - column:
                  name: trigger_type
                  type: VARCHAR(20)
                  remarks: "SCHEDULED, MANUAL"
```

### 5.3 Новые Entity / Enum

```java
// EventSource.java
public enum EventSource {
    MANUAL, KUDAGO, TIMEPAD, TELEGRAM, CULTURE_RU
}

// EventStatus.java
public enum EventStatus {
    ACTIVE,        // Опубликовано
    NEEDS_REVIEW,  // Требует модерации (Telegram)
    REJECTED,      // Отклонено модератором
    ARCHIVED       // Прошедшее / удалённое
}
```

---

## 6. Архитектура парсера

### 6.1 Компоненты

```
┌─────────────────────────────────────────────────────┐
│                  EventParserService                  │
│  (оркестратор: запускает все провайдеры)             │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ KudaGoParser │  │TimepadParser │  │ TgParser  │ │
│  │ implements   │  │ implements   │  │implements │ │
│  │EventProvider │  │EventProvider │  │EventProv. │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         │                 │                │       │
│         └────────┬────────┘────────────────┘       │
│                  ▼                                   │
│        EventNormalizerService                        │
│        (маппинг, валидация, очистка)                 │
│                  │                                   │
│                  ▼                                   │
│        EventDeduplicationService                     │
│        (проверка дубликатов)                         │
│                  │                                   │
│                  ▼                                   │
│        EventRepository.save()                        │
└─────────────────────────────────────────────────────┘
```

### 6.2 Интерфейс провайдера

```java
public interface EventProvider {
    EventSource getSource();
    List<RawExternalEvent> fetchEvents(ParserConfig config);
}

@Data
public class RawExternalEvent {
    private String externalId;
    private EventSource source;
    private String title;
    private String description;
    private String rawCategory;
    private String imageUrl;
    private String rawPrice;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String ticketUrl;
    private String city;
    private Map<String, Object> rawData; // оригинальный JSON для дебага
}
```

### 6.3 Конфигурация (`application.yml`)

```yaml
parser:
  enabled: true
  schedule:
    cron: "0 0 3 * * *"   # каждый день в 03:00 MSK
  sources:
    kudago:
      enabled: true
      base-url: https://kudago.com/public-api/v1.4
      locations:
        - msk
        - spb
      page-size: 100
      max-pages: 50        # защита от бесконечной пагинации
      request-delay-ms: 500
    timepad:
      enabled: true
      base-url: https://api.timepad.ru/v1
      token: ${TIMEPAD_API_TOKEN}
      limit: 100
      max-pages: 50
      request-delay-ms: 200
    telegram:
      enabled: false        # Phase 2
      bot-token: ${TELEGRAM_BOT_TOKEN}
      channels:
        - username: "@afaboroda"
          city: "Москва"
        - username: "@spb_afisha"
          city: "Санкт-Петербург"
      messages-per-channel: 50
    culture-ru:
      enabled: false        # Phase 3
      base-url: https://all.culture.ru/api/2.0
      api-key: ${CULTURE_RU_API_KEY}
  defaults:
    future-days: 90         # парсить события на N дней вперёд
    auto-approve-sources:   # источники без модерации
      - KUDAGO
      - TIMEPAD
    needs-review-sources:   # требуют модерации
      - TELEGRAM
```

---

## 7. Расписание и механизм запуска

### 7.1 Автоматический запуск (Cron)

- **Расписание:** ежедневно в **03:00 MSK** (00:00 UTC)
- **Реализация:** Spring `@Scheduled(cron = "...")`
- **Порядок:** KudaGo → Timepad → Telegram → Culture.ru (последовательно)
- **Таймаут:** макс. 30 минут на весь цикл, 10 минут на каждый источник

### 7.2 Ручной запуск из админки

**Новый endpoint:**

```
POST /api/admin/parser/run
Body (optional): { "sources": ["KUDAGO", "TIMEPAD"] }
Response: { "runId": 42, "status": "STARTED" }
```

```
GET /api/admin/parser/runs
Response: [{ "id": 42, "source": "KUDAGO", "status": "SUCCESS", ... }]
```

```
GET /api/admin/parser/runs/{id}
Response: { "id": 42, ... полные детали ... }
```

### 7.3 Модерация (для Telegram-источников)

```
GET /api/admin/events?status=NEEDS_REVIEW
PUT /api/admin/events/{id}/approve   → status = ACTIVE
PUT /api/admin/events/{id}/reject    → status = REJECTED
```

---

## 8. Дедупликация

### 8.1 Стратегия

Трёхуровневая дедупликация:

1. **По `source` + `external_id`** (unique constraint в БД)  
   — Точное совпадение: одно и то же событие из того же источника.  
   — Действие: UPDATE существующей записи (обновить description, price, image_url, updated_at).

2. **По `title` + `event_date` + `city`** (fuzzy)  
   — Одно событие из разных источников.  
   — Алгоритм: Levenshtein distance на title (порог ≤ 0.2 от длины) + точное совпадение date + city.  
   — Действие: пропустить (сохранить запись от более приоритетного источника).

3. **По `ticket_url`** (точное)  
   — Если URL билета совпадает — это один и тот же ивент.  
   — Действие: пропустить дубликат.

### 8.2 Приоритет источников

```
MANUAL > KUDAGO > TIMEPAD > CULTURE_RU > TELEGRAM
```

Если мероприятие уже существует от более приоритетного источника, дубликат из менее приоритетного игнорируется.

---

## 9. Обработка ошибок

### 9.1 Уровни ошибок

| Уровень | Пример | Действие |
|---------|--------|----------|
| **Source** | API недоступен, 5xx, таймаут | Retry 3 раза с exponential backoff (1s → 2s → 4s). Если все попытки провалились — пометить run как `FAILED`, перейти к следующему источнику |
| **Page** | Ошибка на конкретной странице пагинации | Retry 2 раза. При неудаче — пометить run как `PARTIAL`, продолжить с следующей страницы |
| **Event** | Невалидные данные (нет title, нет даты) | Пропустить событие, инкрементировать `events_skipped`, логировать WARN |
| **System** | OOM, DB connection lost | Пометить run как `FAILED`, выбросить исключение наверх |

### 9.2 Валидация событий

Обязательные поля для сохранения:
- `title` — не пустой, ≤ 255 символов
- `event_date` — не null, не в прошлом (допуск: -1 день)
- `city` — не пустой
- `type` — маппится на EventType (иначе fallback)

### 9.3 Логирование

- Каждый запуск записывается в `parser_runs`
- Детальные логи в SLF4J (logger name: `com.join.back.parser`)
- Уровни: INFO для старт/финиш/счётчики, WARN для пропущенных событий, ERROR для сбоев

### 9.4 Мониторинг (рекомендация)

- Если `events_created = 0` при `status = SUCCESS` — предупреждение (источник мог изменить API)
- Если 3 запуска подряд `FAILED` для одного источника — уведомление в админку

---

## 10. Парсинг цены

Цена в KudaGo приходит строкой (например, `"от 500 руб."`, `"бесплатно"`, `"500–1500 руб."`).

**Алгоритм:**

```java
BigDecimal parsePrice(String rawPrice) {
    if (rawPrice == null || rawPrice.isBlank()) return null;
    
    String lower = rawPrice.toLowerCase().trim();
    if (lower.contains("бесплатно") || lower.equals("free")) return BigDecimal.ZERO;
    
    // Извлечь первое число
    Matcher m = Pattern.compile("(\\d[\\d\\s]*\\d|\\d)").matcher(lower);
    if (m.find()) {
        String digits = m.group(1).replaceAll("\\s+", "");
        return new BigDecimal(digits);
    }
    
    return null; // не удалось распарсить
}
```

---

## 11. План реализации

### Phase 1 (MVP) — 2 недели
- [ ] Миграция БД: новые поля `source`, `external_id`, `status`, `updated_at`
- [ ] Таблица `parser_runs`
- [ ] Интерфейс `EventProvider`, `RawExternalEvent`
- [ ] `KudaGoParser` — полная реализация
- [ ] `EventNormalizerService` — маппинг категорий, городов, цен
- [ ] `EventDeduplicationService` — уровни 1 и 3
- [ ] `EventParserService` — оркестратор с `@Scheduled`
- [ ] Admin API: `POST /parser/run`, `GET /parser/runs`
- [ ] Unit-тесты для маппинга и дедупликации

### Phase 2 — 1 неделя
- [ ] `TimepadParser` — реализация (после получения API-токена)
- [ ] Fuzzy-дедупликация (уровень 2)
- [ ] Кнопка "Запустить парсер" в UI админки

### Phase 3 — 2 недели
- [ ] `TelegramParser` — базовый парсинг сообщений
- [ ] Модерация: UI для `NEEDS_REVIEW` событий
- [ ] `CultureRuParser` — интеграция

### Phase 4 — по необходимости
- [ ] Мониторинг и алерты
- [ ] Автоматическое архивирование прошедших событий
- [ ] Расширение на новые города/источники

---

## 12. Зависимости и конфигурация

### Maven/Gradle зависимости

```groovy
// Уже есть (Spring Boot Starter Web, JPA, etc.)
// Дополнительно:
implementation 'org.jsoup:jsoup:1.17.2'           // HTML cleanup для KudaGo descriptions
implementation 'org.apache.commons:commons-text:1.12.0'  // LevenshteinDistance для fuzzy dedup
```

### Переменные окружения

| Переменная | Описание | Обязательная |
|-----------|----------|:---:|
| `TIMEPAD_API_TOKEN` | OAuth2 токен Timepad | Для Phase 2 |
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота | Для Phase 3 |
| `CULTURE_RU_API_KEY` | API-ключ culture.ru | Для Phase 3 |

---

## 13. Вопросы для обсуждения

1. **Города:** начинаем только с Москвы и СПб или сразу все 11 городов KudaGo?
2. **Частота обновления:** достаточно ли 1 раз в сутки или нужно чаще?
3. **Telegram:** нужен ли парсинг Telegram в MVP или это может подождать?
4. **Модерация:** все внешние мероприятия автоматически публикуются или только проверенные источники?
5. **Хранение изображений:** скачивать и хранить локально или оставить внешние URL?
6. **Timepad токен:** кто будет регистрировать приложение и получать токен?
