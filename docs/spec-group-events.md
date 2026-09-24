# Спецификация: Большая компания (групповые события, 3+ человек)

**Версия:** 1.0  
**Дата:** 2026-03-21  
**Статус:** Draft

---

## 1. Бизнес-контекст

### Текущее состояние
JOIN сейчас поддерживает матчинг **1-к-1**: два пользователя лайкают одно мероприятие → проверяются взаимные критерии → создаётся `Match` + личный `Chat`. Это хорошо для тет-а-тет встреч, но не покрывает сценарии, где хочется прийти компанией.

### Запрос бизнеса
Добавить возможность **собрать группу** (3+ человек) на мероприятие. Пользователь может создать группу с указанием желаемого размера и принять других желающих, либо сам вступить в уже существующую группу.

### Ценность для пользователей
- Больше форматов общения: не только тет-а-тет, но и компания
- Более живая социализация: групповой чат с несколькими людьми
- Снижение барьера входа: не нужно ждать идеального 1-к-1 матча — можно найти группу быстрее

### Ценность для бизнеса
- Рост retention: групповые взаимодействия более «залипательные»
- Дифференциация: уникальная фича среди аналогов
- Потенциал монетизации: premium-группы, приоритетное отображение

---

## 2. User Stories

### US-01: Создание группы
> As a **registered user**, I want to **create a group for an event with a specified maximum size**, so that **I can find companions to attend together**.

### US-02: Просмотр групп на мероприятии
> As a **registered user**, I want to **see available open groups for an event**, so that **I can decide which group to join or whether to create my own**.

### US-03: Вступление в группу
> As a **registered user**, I want to **join an open group for an event**, so that **I can attend with new people**.

### US-04: Групповой чат
> As a **group member**, I want to **chat with all members of my group**, so that **we can coordinate details before the event**.

### US-05: Выход из группы
> As a **group member**, I want to **leave a group I joined**, so that **I can cancel my participation without disrupting others**.

### US-06: Управление группой (создатель)
> As a **group creator**, I want to **close my group early or change its max size**, so that **I have control over who attends with me**.

### US-07: Отображение групп на афише
> As a **user browsing events**, I want to **see how many open groups and total members are gathering for each event**, so that **I can gauge social activity around events**.

---

## 3. Acceptance Criteria

### AC-01: Создание группы
- [ ] Пользователь может создать группу на событии, указав `maxSize` (от 3 до 20)
- [ ] Создатель автоматически становится первым участником группы
- [ ] Нельзя создать вторую группу на то же мероприятие, если уже состоишь в группе на него
- [ ] Нельзя создать группу на событие с прошедшей датой
- [ ] При создании группы автоматически создаётся групповой чат

### AC-02: Просмотр групп
- [ ] Список открытых групп доступен для любого авторизованного пользователя
- [ ] В списке отображаются: название группы (опционально), число участников / максимум, фото создателя, дата создания
- [ ] Закрытые (`CLOSED`) и заполненные (`FULL`) группы не отображаются в списке

### AC-03: Вступление в группу
- [ ] Пользователь может вступить в открытую (`OPEN`) группу
- [ ] Нельзя вступить в группу на мероприятие, если уже состоишь в другой группе на него
- [ ] Нельзя вступить в свою же группу (создатель уже в ней)
- [ ] При достижении `maxSize` статус группы автоматически становится `FULL`
- [ ] При вступлении пользователь добавляется в групповой чат

### AC-04: Групповой чат
- [ ] Все участники группы видят и могут отправлять сообщения в групповой чат
- [ ] Новые сообщения доставляются в реальном времени через WebSocket всем участникам группы
- [ ] Пользователь, покинувший группу, теряет доступ к чату
- [ ] Системное сообщение при вступлении/выходе участника (опционально, v2)

### AC-05: Выход из группы
- [ ] Участник может покинуть группу в любой момент (статус записи → `LEFT`)
- [ ] Создатель может покинуть группу, если в ней есть другие участники → лидерство передаётся следующему по дате вступления
- [ ] Если создатель покидает группу и в ней больше нет участников → группа переходит в статус `CLOSED`
- [ ] После выхода статус группы пересчитывается: если была `FULL` и один вышел → снова `OPEN`

### AC-06: Управление группой
- [ ] Только создатель может закрыть группу (`CLOSED`) — принятие новых участников запрещается
- [ ] Только создатель может уменьшить `maxSize` (но не ниже текущего числа участников)
- [ ] Только создатель может увеличить `maxSize` (до 20)

### AC-07: Отображение на афише
- [ ] На карточке события отображается число открытых групп и суммарное число участников в них
- [ ] Индикатор обновляется при загрузке страницы (не real-time)

### AC-08: Backward Compatibility
- [ ] Существующий 1-к-1 матчинг через `EventLike` продолжает работать без изменений
- [ ] Лайк события и создание/вступление в группу — независимые действия
- [ ] Существующие `chats` и `matches` таблицы не затронуты

---

## 4. Data Model Changes

### 4.1 Новые таблицы

#### `group_gatherings` — группа, собирающаяся на мероприятие

```sql
CREATE TABLE group_gatherings (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id),
    creator_id  BIGINT NOT NULL REFERENCES users(id),
    title       VARCHAR(100),                         -- опциональное название группы
    description TEXT,                                 -- опциональное описание
    max_size    INT NOT NULL DEFAULT 5,                -- желаемый размер (3–20)
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN | FULL | CLOSED
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP
);
-- Индексы
CREATE INDEX idx_group_gatherings_event_id ON group_gatherings(event_id);
CREATE INDEX idx_group_gatherings_creator_id ON group_gatherings(creator_id);
CREATE INDEX idx_group_gatherings_status ON group_gatherings(status);
```

#### `group_members` — участники группы

```sql
CREATE TABLE group_members (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT NOT NULL REFERENCES group_gatherings(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- CREATOR | MEMBER
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | LEFT
    joined_at   TIMESTAMP NOT NULL,
    left_at     TIMESTAMP,
    UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_members_group_id ON group_members(group_id);
CREATE INDEX idx_group_members_user_id ON group_members(user_id);
```

#### `group_chats` — групповой чат

```sql
CREATE TABLE group_chats (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT NOT NULL UNIQUE REFERENCES group_gatherings(id),
    event_id    BIGINT NOT NULL REFERENCES events(id),
    created_at  TIMESTAMP NOT NULL
);
```

#### `group_chat_messages` — сообщения группового чата

```sql
CREATE TABLE group_chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    group_chat_id BIGINT NOT NULL REFERENCES group_chats(id),
    sender_id    BIGINT NOT NULL REFERENCES users(id),
    text         TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_group_chat_messages_chat_id ON group_chat_messages(group_chat_id);
CREATE INDEX idx_group_chat_messages_created_at ON group_chat_messages(group_chat_id, created_at);
```

> **Примечание по read-статусу:** Поле `is_read` в `group_chat_messages` применимо для 1-к-1, но в групповых чатах нужна таблица read-receipts. Для v1 — считать все сообщения прочитанными при открытии чата (без per-user tracking). Per-user read tracking вынести в v2.

### 4.2 Изменения в существующих таблицах

**Таблица `events`:** изменений нет. Статистика групп считается JOIN-запросом.

**Таблицы `matches`, `chats`, `chat_messages`:** изменений нет.

### 4.3 Enum значения (Java)

```java
public enum GroupStatus { OPEN, FULL, CLOSED }
public enum GroupMemberRole { CREATOR, MEMBER }
public enum GroupMemberStatus { ACTIVE, LEFT }
```

---

## 5. API Contract

### Базовый URL: `/api`

---

### 5.1 POST /api/events/{eventId}/groups — создать группу

**Auth:** требуется  
**Description:** Создаёт новую открытую группу на мероприятие. Создатель автоматически становится первым участником.

**Path params:**
- `eventId` (Long) — ID мероприятия

**Request body:**
```json
{
  "title": "Идём на джаз! 🎷",
  "description": "Ищем позитивных людей, после концерта в бар",
  "maxSize": 5
}
```

| Поле | Тип | Обязательно | Ограничения |
|------|-----|-------------|-------------|
| `title` | String | нет | max 100 символов |
| `description` | String | нет | max 500 символов |
| `maxSize` | Integer | да | 3–20 |

**Response 201 Created:**
```json
{
  "id": 42,
  "eventId": 100,
  "title": "Идём на джаз! 🎷",
  "description": "Ищем позитивных людей, после концерта в бар",
  "maxSize": 5,
  "currentSize": 1,
  "status": "OPEN",
  "creator": {
    "id": 7,
    "firstName": "Алексей",
    "photo": "https://..."
  },
  "groupChatId": 15,
  "createdAt": "2026-03-21T12:00:00"
}
```

**Errors:**
- `400 Bad Request` — maxSize < 3 или > 20
- `404 Not Found` — событие не найдено
- `409 Conflict` — пользователь уже состоит в группе на это событие
- `422 Unprocessable Entity` — событие уже прошло

---

### 5.2 GET /api/events/{eventId}/groups — список групп на событие

**Auth:** требуется  
**Description:** Возвращает открытые (`OPEN`) группы для мероприятия. Группы `FULL` и `CLOSED` не включаются.

**Path params:**
- `eventId` (Long)

**Query params:**
- `page` (int, default 0)
- `size` (int, default 20)

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": 42,
      "eventId": 100,
      "title": "Идём на джаз! 🎷",
      "description": "Ищем позитивных людей, после концерта в бар",
      "maxSize": 5,
      "currentSize": 2,
      "status": "OPEN",
      "creator": {
        "id": 7,
        "firstName": "Алексей",
        "photo": "https://..."
      },
      "memberPreviews": [
        { "id": 7, "firstName": "Алексей", "photo": "https://..." },
        { "id": 9, "firstName": "Мария", "photo": "https://..." }
      ],
      "createdAt": "2026-03-21T12:00:00"
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

**Errors:**
- `404 Not Found` — событие не найдено

---

### 5.3 GET /api/groups/{groupId} — детали группы

**Auth:** требуется

**Response 200 OK:**
```json
{
  "id": 42,
  "eventId": 100,
  "eventTitle": "Jazz Under The Stars",
  "eventDate": "2026-04-15",
  "title": "Идём на джаз! 🎷",
  "description": "Ищем позитивных людей, после концерта в бар",
  "maxSize": 5,
  "currentSize": 2,
  "status": "OPEN",
  "groupChatId": 15,
  "members": [
    {
      "userId": 7,
      "firstName": "Алексей",
      "photo": "https://...",
      "role": "CREATOR",
      "joinedAt": "2026-03-21T12:00:00"
    },
    {
      "userId": 9,
      "firstName": "Мария",
      "photo": "https://...",
      "role": "MEMBER",
      "joinedAt": "2026-03-21T12:30:00"
    }
  ],
  "createdAt": "2026-03-21T12:00:00"
}
```

**Errors:**
- `404 Not Found` — группа не найдена

---

### 5.4 POST /api/groups/{groupId}/join — вступить в группу

**Auth:** требуется

**Request body:** нет

**Response 200 OK:**
```json
{
  "groupId": 42,
  "groupChatId": 15,
  "message": "You have successfully joined the group"
}
```

**Errors:**
- `404 Not Found` — группа не найдена
- `409 Conflict` — уже состоишь в группе на это событие (code: `ALREADY_IN_GROUP_FOR_EVENT`)
- `409 Conflict` — группа заполнена (`FULL`) или закрыта (`CLOSED`) (code: `GROUP_NOT_OPEN`)
- `409 Conflict` — пытаешься вступить в свою группу (code: `CANNOT_JOIN_OWN_GROUP`)

---

### 5.5 POST /api/groups/{groupId}/leave — покинуть группу

**Auth:** требуется

**Request body:** нет

**Response 200 OK:**
```json
{
  "groupId": 42,
  "message": "You have left the group"
}
```

**Errors:**
- `404 Not Found` — группа не найдена
- `409 Conflict` — пользователь не является участником группы

---

### 5.6 PATCH /api/groups/{groupId} — обновить группу (только создатель)

**Auth:** требуется

**Request body** (все поля опциональны):
```json
{
  "title": "Джаз и бар после!",
  "description": "Обновлённое описание",
  "maxSize": 6,
  "status": "CLOSED"
}
```

| Поле | Допустимые изменения |
|------|---------------------|
| `title` | Любая строка до 100 символов |
| `description` | Любая строка до 500 символов |
| `maxSize` | Увеличить до 20; уменьшить, но не ниже `currentSize` |
| `status` | Только `OPEN` → `CLOSED` (создатель закрывает группу вручную) |

**Response 200 OK:** возвращает обновлённый объект группы (как в GET /api/groups/{groupId})

**Errors:**
- `403 Forbidden` — не создатель группы
- `404 Not Found` — группа не найдена
- `400 Bad Request` — попытка уменьшить maxSize ниже текущего числа участников
- `400 Bad Request` — недопустимый переход статуса

---

### 5.7 GET /api/groups — мои группы

**Auth:** требуется  
**Description:** Возвращает все группы, в которых пользователь является активным участником (status = ACTIVE).

**Query params:**
- `page` (int, default 0)
- `size` (int, default 20)

**Response 200 OK:** массив групп (структура как в GET /api/events/{eventId}/groups, добавлено поле `eventTitle`, `eventDate`)

---

### 5.8 GET /api/group-chats/{groupChatId}/messages — история сообщений

**Auth:** требуется  
**Description:** Только для участников группы (role: ACTIVE member).

**Query params:**
- `page` (int, default 0)
- `size` (int, default 50)

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": 101,
      "senderId": 7,
      "senderName": "Алексей",
      "senderPhoto": "https://...",
      "text": "Привет всем! Кто идёт?",
      "createdAt": "2026-03-21T12:05:00"
    }
  ],
  "totalElements": 15,
  "totalPages": 1,
  "page": 0,
  "size": 50
}
```

**Errors:**
- `403 Forbidden` — пользователь не является активным участником группы
- `404 Not Found` — чат не найден

---

### 5.9 POST /api/group-chats/{groupChatId}/messages — отправить сообщение

**Auth:** требуется

**Request body:**
```json
{
  "text": "Встречаемся у главного входа в 18:45!"
}
```

**Response 201 Created:**
```json
{
  "id": 102,
  "senderId": 9,
  "senderName": "Мария",
  "senderPhoto": "https://...",
  "text": "Встречаемся у главного входа в 18:45!",
  "createdAt": "2026-03-21T13:00:00"
}
```

**Errors:**
- `403 Forbidden` — пользователь не является активным участником
- `404 Not Found` — чат не найден
- `400 Bad Request` — пустой текст

---

### 5.10 GET /api/events/{eventId}/groups/stats — статистика групп для афиши

**Auth:** требуется  
**Description:** Лёгкий endpoint для отображения активности на карточке события.

**Response 200 OK:**
```json
{
  "eventId": 100,
  "openGroupsCount": 3,
  "totalActiveMembers": 11
}
```

---

### 5.11 WebSocket — групповой чат

**Endpoint:** `/ws` (STOMP over WebSocket, существующий)

**Subscribe (клиент → сервер):**
```
SUBSCRIBE /topic/group/{groupChatId}
```

**Publish (клиент → сервер):**
```
SEND /app/group/{groupChatId}/message
Body: { "text": "..." }
```

**Broadcast (сервер → все подписчики топика):**
```json
{
  "id": 102,
  "senderId": 9,
  "senderName": "Мария",
  "senderPhoto": "https://...",
  "text": "Встречаемся у главного входа в 18:45!",
  "createdAt": "2026-03-21T13:00:00"
}
```

> **Авторизация WebSocket:** при STOMP CONNECT используется тот же `X-Telegram-Init-Data` header. Проверить, что `WebSocketConfig` пропускает header в STOMP handshake. Перед отправкой в топик сервер проверяет, что отправитель является активным участником группы.

---

## 6. UI Requirements

### 6.1 Экран деталей события (EventDetailScreen)

**Секция "Компания" под основной информацией:**
- Заголовок: "Собери компанию" или "Открытые группы (N)"
- Если нет открытых групп → кнопка "Создать группу"
- Если есть группы → карточки групп (preview: фото участников, N/M мест) + кнопка "Создать свою"
- Лимит preview: 3 группы, кнопка "Смотреть все"

**Состояния:**
- `loading` — скелетон-placeholder
- `empty` — "Стань первым! Создай компанию."
- `error` — "Не удалось загрузить группы. Попробуй ещё раз."

---

### 6.2 Экран списка групп (GroupsListScreen)

**URL:** `/events/{eventId}/groups`

- Список карточек групп
- Карточка группы: название (или "Группа #N"), описание (обрезанное), фото участников (стек до 3), прогресс N/M, кнопка "Вступить"
- Кнопка FAB "Создать группу"
- Пагинация через scroll (infinite scroll)

**Состояния карточки:**
- Активная кнопка "Вступить" — группа `OPEN`, пользователь не участник
- "Ты здесь" — пользователь уже участник этой группы
- Кнопка скрыта — группа `FULL` или `CLOSED`

---

### 6.3 Модальное окно / экран создания группы

**URL:** `/events/{eventId}/groups/new`

- Поле: Название группы (опционально, placeholder: "Например: Идём вместе!")
- Поле: Описание (textarea, опционально)
- Slider / selector: Размер группы (3–20, default 5)
- Кнопка "Создать"
- После создания → редирект в групповой чат

---

### 6.4 Экран деталей группы (GroupDetailScreen)

**URL:** `/groups/{groupId}`

- Название группы, описание
- Мероприятие: название, дата, ссылка на событие
- Список участников: фото + имя + роль (создатель выделен)
- Прогресс-бар: N/M участников
- Кнопка "Открыть чат" (если участник)
- Кнопка "Вступить" (если не участник и группа OPEN)
- Кнопка "Покинуть группу" (если участник, не создатель)
- Для создателя: кнопки "Закрыть группу", "Изменить настройки"

---

### 6.5 Экран группового чата (GroupChatScreen)

**URL:** `/group-chats/{groupChatId}`

- Header: название группы, N участников, кнопка инфо (→ GroupDetailScreen)
- Сообщения: сортировка по времени ASC, пагинация (oldest first, scroll up to load more)
- Аватар и имя отправителя отображаются (в отличие от 1-к-1)
- Инпут сообщения с кнопкой отправки
- WebSocket: новые сообщения появляются в реальном времени без обновления страницы

---

### 6.6 Раздел "Мои группы" в ChatsListScreen

В экране `ChatsListScreen` добавить вкладки:
- **Личные чаты** (существующий функционал)
- **Группы** (новый: список активных групп пользователя)

Карточка группы в списке: название события, название группы, последнее сообщение, время, бейдж непрочитанных (v2).

---

## 7. Edge Cases

### EC-01: Пользователь лайкает событие И состоит в группе на него
- Лайк и участие в группе — **независимые действия**. Пользователь может лайкнуть событие (для 1-к-1 матча) и одновременно состоять в группе на то же событие. Никакого конфликта нет.

### EC-02: Создатель покидает группу
- Если в группе ≥ 2 участника: создатель может выйти → роль `CREATOR` передаётся участнику с самым ранним `joined_at`
- Если создатель — единственный участник: группа переходит в `CLOSED`

### EC-03: Событие прошло
- Группы на прошедшие события не отображаются в списке поиска
- Существующие участники продолжают видеть группу в "Мои группы" и имеют доступ к чату
- Создать новую группу на прошедшее событие нельзя (422)

### EC-04: Пользователь удалил лайк с события, состоя в группе
- Отмена лайка (`unlike`) не влияет на членство в группе. Группа и лайк — независимые сущности.

### EC-05: Одновременные запросы на вступление (race condition)
- При одновременном вступлении нескольких пользователей возможно превышение `maxSize`
- Защита: `@Transactional` + pessimistic lock на `group_gatherings` при изменении `currentSize` ИЛИ проверка перед INSERT в `group_members` + поле `UNIQUE(group_id, user_id)`
- Рекомендация: использовать `SELECT FOR UPDATE` на строку `group_gatherings` в сервисе при вступлении

### EC-06: Пользователь уже в группе на это событие
- Попытка вступить в другую группу на то же событие → `409 Conflict` (code: `ALREADY_IN_GROUP_FOR_EVENT`)
- Попытка создать группу, если уже в группе на это событие → `409 Conflict`

### EC-07: Изменение maxSize ниже текущего числа участников
- Запрос `PATCH` с `maxSize` < `currentSize` → `400 Bad Request`
- Если maxSize уменьшается до = currentSize и группа была OPEN → статус автоматически меняется на FULL

### EC-08: Группа становится полной
- После вступления последнего участника (currentSize == maxSize) → сервис автоматически обновляет `status` = `FULL`
- После выхода одного участника из FULL группы → статус снова `OPEN`

### EC-09: Попытка написать в чат покинутой группы
- Пользователь покинул группу → его `group_members.status` = `LEFT`
- Попытка отправить сообщение → `403 Forbidden`
- Попытка подписаться на WebSocket топик → сервер отклоняет (проверка при SEND)

### EC-10: Удаление пользователя из системы
- Если пользователь удаляет аккаунт → его записи в `group_members` должны быть помечены `LEFT` или обработаны каскадом (согласовать с командой удаления аккаунтов)

### EC-11: maxSize = 2
- Минимум 3, т.к. это "большая компания". Запрос с maxSize < 3 → `400 Bad Request`.

---

## 8. Влияние на существующий матчинг (Backward Compatibility)

### 8.1 Что НЕ меняется
| Компонент | Статус |
|-----------|--------|
| `EventLike` entity | ✅ Без изменений |
| `Match` entity | ✅ Без изменений |
| `Chat` entity | ✅ Без изменений |
| `ChatMessage` entity | ✅ Без изменений |
| `LikeService.like()` | ✅ Без изменений |
| `MatchService.checkAndCreateMatch()` | ✅ Без изменений |
| `ChatService` (личные чаты) | ✅ Без изменений |
| API `/api/events`, `/api/likes`, `/api/matches`, `/api/chats` | ✅ Без изменений |
| WebSocket 1-к-1 `/queue/messages` | ✅ Без изменений |

### 8.2 Что добавляется (аддитивно)
- Новые таблицы: `group_gatherings`, `group_members`, `group_chats`, `group_chat_messages`
- Новые endpoints: `/api/events/{id}/groups`, `/api/groups/**`, `/api/group-chats/**`
- Новый WebSocket топик: `/topic/group/{groupChatId}`
- Новые Liquibase миграции (не затрагивают существующие таблицы)

### 8.3 Потенциально затрагиваемые компоненты
- **`ChatsListScreen` (frontend):** добавление второй вкладки "Группы" — изменение UI, но не ломает текущий функционал
- **`WebSocketConfig`:** возможно потребуется настройка message broker для broadcast в topic (проверить текущую конфигурацию)
- **`SecurityConfig`:** добавить публичные паттерны для новых endpoints если нужно (скорее всего — все под auth)

### 8.4 Риски
- **WebSocket broadcast:** текущая реализация `ChatService.sendMessage()` использует `convertAndSendToUser()` (1-к-1). Для групп нужен `convertAndSend()` в topic. Убедиться, что `WebSocketConfig` включает `/topic/**` в message broker.
- **Нагрузка на БД:** при большом числе участников в группе broadcast уведомлений может быть дорогим. В v1 это ОК, в v2 рассмотреть Redis pub/sub.

---

## 9. Зависимости

### 9.1 Существующие компоненты, от которых зависит фича
- `User` entity — участники группы
- `Event` entity — мероприятие, к которому привязана группа
- `TelegramAuthFilter` — аутентификация, используется как есть
- `WebSocketConfig` — WebSocket инфраструктура для группового чата
- `NotificationService` — может использоваться для уведомлений (новый участник вступил в группу)

### 9.2 Новые компоненты (для разработки)
| Слой | Компоненты |
|------|-----------|
| Entity | `GroupGathering`, `GroupMember`, `GroupChat`, `GroupChatMessage` |
| Repository | `GroupGatheringRepository`, `GroupMemberRepository`, `GroupChatRepository`, `GroupChatMessageRepository` |
| Service | `GroupGatheringService`, `GroupChatService` |
| Controller | `GroupGatheringController`, `GroupChatController` |
| DTO | `CreateGroupRequest`, `GroupResponse`, `GroupDetailResponse`, `GroupChatMessageResponse`, `GroupStatsResponse` |
| Mapper | `GroupMapper` |
| Migration | Liquibase changeset (4 новые таблицы) |

---

## 10. Нерешённые вопросы (требуют решения директора)

| # | Вопрос | Варианты |
|---|--------|----------|
| Q1 | Нужна ли модерация групп? Может ли создатель удалить участника? | A: Да (сложнее) / B: Нет (v1 без кика) |
| Q2 | Отображать группы `FULL` в списке (как "посмотреть, кто идёт")? | A: Да, отдельная секция / B: Нет, только OPEN |
| Q3 | Уведомления: отправлять push при вступлении нового участника? | A: Да, через NotificationService / B: Нет в v1 |
| Q4 | Лимит групп на одно мероприятие? | A: Без лимита / B: N групп на событие |
| Q5 | Критерии совместимости для группы? Проверять preferred_gender/age как в 1-к-1? | A: Да / B: Нет, группа полностью открытая |
| Q6 | Нужен ли read-receipt per user в групповом чате (для бейджа непрочитанных)? | A: v1 без / B: Сразу с таблицей read_receipts |

---

## 11. Liquibase Migration Plan

```
db/changelog/
  ├── 001_create_events.xml          (существующий)
  ├── 002_insert_sample_events.xml   (существующий)
  ├── ...
  ├── 0NN_create_group_gatherings.xml   (НОВЫЙ)
  ├── 0NN+1_create_group_members.xml    (НОВЫЙ)
  ├── 0NN+2_create_group_chats.xml      (НОВЫЙ)
  └── 0NN+3_create_group_chat_messages.xml (НОВЫЙ)
```

Каждый файл содержит `preConditions` (проверка, что таблица не существует) + `createTable` changeset. Откат: `dropTable`.

---

## 12. Sequence Diagram — Создание группы и вступление участника

```
User A                    Backend                  Database
  │                          │                        │
  │ POST /api/events/100/    │                        │
  │ groups {maxSize:5}       │                        │
  │──────────────────────────►                        │
  │                          │ Validate event exists  │
  │                          │────────────────────────►
  │                          │◄───────────────────────│
  │                          │ Check user not in group│
  │                          │────────────────────────►
  │                          │◄───────────────────────│
  │                          │ INSERT group_gatherings│
  │                          │────────────────────────►
  │                          │ INSERT group_members   │
  │                          │ (role=CREATOR, status=ACTIVE)
  │                          │────────────────────────►
  │                          │ INSERT group_chats     │
  │                          │────────────────────────►
  │◄──────────────────────────                        │
  │ 201 {id:42,chatId:15}    │                        │
  │                          │                        │
User B                    Backend                  Database
  │                          │                        │
  │ POST /api/groups/42/join │                        │
  │──────────────────────────►                        │
  │                          │ SELECT FOR UPDATE      │
  │                          │ group_gatherings WHERE │
  │                          │ id=42 (lock row)       │
  │                          │────────────────────────►
  │                          │ Validate: OPEN, not full
  │                          │ Check user not in group│
  │                          │────────────────────────►
  │                          │◄───────────────────────│
  │                          │ INSERT group_members   │
  │                          │ UPDATE currentSize=2   │
  │                          │────────────────────────►
  │◄──────────────────────────                        │
  │ 200 {groupId:42,         │                        │
  │      groupChatId:15}     │                        │
```

---

