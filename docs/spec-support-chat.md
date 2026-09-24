# Спецификация: Чат Поддержки + Админка оператора

**Версия:** 1.0  
**Дата:** 2026-03-21  
**Статус:** Draft  

---

## Бизнес-контекст

Проект JOIN — Telegram Mini App для поиска компаньонов на мероприятия. По мере роста аудитории пользователям нужен канал обратной связи с командой: вопросы, жалобы, баг-репорты, предложения.

**Что делаем:** Чат поддержки — возможность пользователю написать обращение напрямую команде JOIN, а оператору — отвечать из единой очереди в админке.

**Зачем:**
- Снизить количество необработанных обращений через Telegram-личку разработчиков
- Дать пользователю официальный, удобный канал связи прямо внутри приложения
- Оператору — видеть все обращения в одном месте, отвечать в реальном времени

**Отличие от существующего чата:**  
Существующие `Chat` / `ChatMessage` сущности обслуживают **пользовательский чат компаньонов** (между двумя пользователями по мэтчу). Чат поддержки — принципиально другая сущность: один пользователь ↔ поддержка (оператор, не Telegram-пользователь). Они хранятся отдельно.

---

## User Stories

### Пользователь

- **US-1.** As a user, I want to open a support chat and send a message to the support team, so that I can get help with my problem without leaving the app.
- **US-2.** As a user, I want to see the history of my support conversation, so that I can track the progress of my request.
- **US-3.** As a user, I want to receive the operator's reply in real time (via WebSocket), so that I don't have to refresh the page.
- **US-4.** As a user, I want to see a status indicator when the operator reads my message, so that I know my issue is being handled.

### Оператор (Adminка)

- **US-5.** As an operator, I want to see a list of all support tickets (one per user), sorted by last activity, so that I can prioritize unanswered requests.
- **US-6.** As an operator, I want to see unread message count per ticket, so that I immediately know which tickets need attention.
- **US-7.** As an operator, I want to open any ticket and read the full message history, so that I can understand the user's context before replying.
- **US-8.** As an operator, I want to send a reply to a user from the admin panel, so that the user receives it instantly.
- **US-9.** As an operator, I want new messages from users to appear in real time (WebSocket), so that I don't miss incoming requests.
- **US-10.** As an operator, I want to see user's name and Telegram ID in the ticket, so that I can identify who is writing.

---

## Acceptance Criteria

### Пользовательская часть (фронтенд)

- [ ] **AC-1.** На экране поддержки пользователь видит кнопку «Написать в поддержку» или открытый чат, если обращение уже существует.
- [ ] **AC-2.** Пользователь может отправить текстовое сообщение длиной от 1 до 2000 символов.
- [ ] **AC-3.** Отправленное сообщение сразу появляется в интерфейсе (оптимистичный UI или после подтверждения ответа сервера).
- [ ] **AC-4.** Ответы оператора приходят по WebSocket без перезагрузки страницы.
- [ ] **AC-5.** История сообщений загружается постранично (20 сообщений, scroll-up для загрузки старых).
- [ ] **AC-6.** Сообщения пользователя визуально отличаются от сообщений оператора (цвет/сторона).
- [ ] **AC-7.** При открытии чата непрочитанные сообщения оператора автоматически помечаются как прочитанные.
- [ ] **AC-8.** Пустое состояние: «Нет сообщений — напишите нам!»
- [ ] **AC-9.** Состояние ошибки: при сбое сети — toast-уведомление, сообщение не теряется.
- [ ] **AC-10.** Загрузочное состояние: skeleton/spinner при первой загрузке истории.

### Операторская часть (Adminка)

- [ ] **AC-11.** На вкладке «Support» в админке отображается список всех тикетов.
- [ ] **AC-12.** Каждый тикет в списке: имя пользователя, Telegram ID, превью последнего сообщения, время, бейдж с количеством непрочитанных.
- [ ] **AC-13.** Тикеты отсортированы по времени последнего сообщения (desc).
- [ ] **AC-14.** Оператор может открыть любой тикет и увидеть полную переписку.
- [ ] **AC-15.** Оператор может отправить ответ; сообщение сохраняется в БД и доставляется пользователю через WebSocket.
- [ ] **AC-16.** Новые входящие сообщения от пользователей появляются в реальном времени (без refresh).
- [ ] **AC-17.** При открытии тикета оператором все сообщения помечаются как прочитанные.
- [ ] **AC-18.** Авторизация оператора — HTTP Basic (уже настроено в SecurityConfig: `/api/admin/**` → ROLE_ADMIN).

---

## API Contract

### Аутентификация
- **Пользователь** → `X-Telegram-Init-Data` header (существующий механизм)
- **Оператор** → HTTP Basic Auth (`/api/admin/**` — уже настроено)

---

### REST API — Пользовательская часть

#### GET /api/support/ticket
Получить тикет текущего пользователя (создаётся автоматически при первом запросе).

**Auth:** Telegram  
**Response 200:**
```json
{
  "id": 1,
  "userId": 42,
  "status": "OPEN",
  "createdAt": "2026-03-21T10:00:00",
  "lastMessageAt": "2026-03-21T12:30:00",
  "unreadCount": 2
}
```

**Логика:** если тикета ещё нет — создать и вернуть новый (status=OPEN).

---

#### GET /api/support/ticket/messages?page=0&size=20
Получить историю сообщений тикета текущего пользователя (pageable, desc по createdAt).

**Auth:** Telegram  
**Response 200:**
```json
{
  "content": [
    {
      "id": 10,
      "ticketId": 1,
      "senderType": "USER",
      "text": "Привет, не могу найти мэтч",
      "createdAt": "2026-03-21T10:05:00",
      "isRead": true
    },
    {
      "id": 11,
      "ticketId": 1,
      "senderType": "OPERATOR",
      "text": "Здравствуйте! Уже смотрим на проблему",
      "createdAt": "2026-03-21T10:10:00",
      "isRead": false
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

#### POST /api/support/ticket/messages
Отправить сообщение в свой тикет.

**Auth:** Telegram  
**Request:**
```json
{
  "text": "Мой вопрос..."
}
```
**Validation:** `text` — not blank, max 2000 символов.

**Response 200:**
```json
{
  "id": 12,
  "ticketId": 1,
  "senderType": "USER",
  "text": "Мой вопрос...",
  "createdAt": "2026-03-21T12:31:00",
  "isRead": false
}
```

**Side effect:** push через WebSocket в топик оператора `/topic/support/operator`.

---

#### PUT /api/support/ticket/read
Пометить все сообщения оператора в тикете пользователя как прочитанные.

**Auth:** Telegram  
**Response 200:** (пустой body)

---

### REST API — Операторская часть (admin)

#### GET /api/admin/support/tickets?page=0&size=50
Список всех тикетов поддержки.

**Auth:** HTTP Basic (ROLE_ADMIN)  
**Response 200:**
```json
[
  {
    "id": 1,
    "userId": 42,
    "userFirstName": "Иван",
    "userTelegramId": 123456789,
    "status": "OPEN",
    "lastMessageAt": "2026-03-21T12:30:00",
    "lastMessagePreview": "Не могу найти мэтч...",
    "unreadCount": 1,
    "createdAt": "2026-03-21T10:00:00"
  }
]
```

---

#### GET /api/admin/support/tickets/{ticketId}/messages?page=0&size=20
Полная история сообщений конкретного тикета.

**Auth:** HTTP Basic (ROLE_ADMIN)  
**Response 200:** Аналогично `/api/support/ticket/messages` — массив `SupportMessageResponse`.

---

#### POST /api/admin/support/tickets/{ticketId}/messages
Ответ оператора на тикет.

**Auth:** HTTP Basic (ROLE_ADMIN)  
**Request:**
```json
{
  "text": "Ответ оператора..."
}
```
**Validation:** `text` — not blank, max 2000 символов.

**Response 200:**
```json
{
  "id": 13,
  "ticketId": 1,
  "senderType": "OPERATOR",
  "text": "Ответ оператора...",
  "createdAt": "2026-03-21T12:35:00",
  "isRead": false
}
```

**Side effect:** push через WebSocket пользователю `/queue/support`.

---

#### PUT /api/admin/support/tickets/{ticketId}/read
Пометить все сообщения пользователя в тикете как прочитанные (оператор открыл тикет).

**Auth:** HTTP Basic (ROLE_ADMIN)  
**Response 200:** (пустой body)

---

#### PUT /api/admin/support/tickets/{ticketId}/status
Изменить статус тикета (OPEN → CLOSED и обратно).

**Auth:** HTTP Basic (ROLE_ADMIN)  
**Request:**
```json
{
  "status": "CLOSED"
}
```
**Response 200:**
```json
{
  "id": 1,
  "status": "CLOSED"
}
```

---

### WebSocket API (STOMP)

Использует существующий `/ws` endpoint + SockJS.  
Existing broker: `/topic`, `/queue`. Application prefix: `/app`.

#### Подписки пользователя
| Destination | Направление | Описание |
|---|---|---|
| `/user/queue/support` | сервер → пользователь | Новое сообщение от оператора |

Payload: `SupportMessageResponse` (JSON, идентично REST response).

#### Подписки оператора
| Destination | Направление | Описание |
|---|---|---|
| `/topic/support/operator` | сервер → все операторы | Новое сообщение от любого пользователя |

Payload:
```json
{
  "ticketId": 1,
  "userId": 42,
  "userFirstName": "Иван",
  "message": {
    "id": 12,
    "senderType": "USER",
    "text": "Мой вопрос...",
    "createdAt": "2026-03-21T12:31:00"
  }
}
```

**Примечание:** Оператор авторизован через HTTP Basic. WebSocket STOMP подключение для админки делается с заголовком `Authorization: Basic <base64>` на handshake уровне. Нужно расширить WebSocket security (см. раздел «Зависимости»).

---

## Data Model Changes

### Новая таблица: `support_tickets`

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | NO | — |
| `user_id` | BIGINT FK → users(id) | NO | Пользователь-владелец тикета |
| `status` | VARCHAR(20) | NO | `OPEN` / `CLOSED`, default `OPEN` |
| `created_at` | TIMESTAMP | NO | default CURRENT_TIMESTAMP |
| `last_message_at` | TIMESTAMP | YES | Обновляется при каждом новом сообщении |

**Constraints:**
- `UNIQUE (user_id)` — один тикет на пользователя  
- Index: `idx_support_tickets_status` на `status`
- Index: `idx_support_tickets_last_message_at` на `last_message_at DESC`

---

### Новая таблица: `support_messages`

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | NO | — |
| `ticket_id` | BIGINT FK → support_tickets(id) | NO | — |
| `sender_type` | VARCHAR(20) | NO | `USER` / `OPERATOR` |
| `text` | TEXT | NO | Текст сообщения |
| `created_at` | TIMESTAMP | NO | default CURRENT_TIMESTAMP |
| `is_read` | BOOLEAN | NO | default FALSE |

**Index:** `idx_support_messages_ticket_id` на `ticket_id`

---

### Новые enum

```java
// SupportTicketStatus.java
public enum SupportTicketStatus {
    OPEN, CLOSED
}

// SupportSenderType.java
public enum SupportSenderType {
    USER, OPERATOR
}
```

---

### Liquibase Migrations

**Файл:** `012-create-support-tickets-table.yaml`  
- Создать таблицу `support_tickets`  
- Уникальный индекс на `user_id`  
- Индексы на `status`, `last_message_at`

**Файл:** `013-create-support-messages-table.yaml`  
- Создать таблицу `support_messages`  
- Индекс на `ticket_id`

Нумерация продолжает существующую (последняя: `011-add-university-to-users.yaml`).

---

## Новые Java-сущности и DTO

### Entities

**`SupportTicket.java`** (entity, table `support_tickets`):
- `Long id`
- `Long userId`
- `SupportTicketStatus status`
- `LocalDateTime createdAt`
- `LocalDateTime lastMessageAt`

**`SupportMessage.java`** (entity, table `support_messages`):
- `Long id`
- `Long ticketId`
- `SupportSenderType senderType`
- `String text`
- `LocalDateTime createdAt`
- `boolean isRead`

### DTO (records)

**`SupportTicketResponse`**:
```java
record SupportTicketResponse(
    Long id, Long userId, String userFirstName, Long userTelegramId,
    SupportTicketStatus status, LocalDateTime createdAt,
    LocalDateTime lastMessageAt, String lastMessagePreview, long unreadCount
)
```

**`SupportMessageResponse`**:
```java
record SupportMessageResponse(
    Long id, Long ticketId, SupportSenderType senderType,
    String text, LocalDateTime createdAt, boolean isRead
)
```

**`SendSupportMessageRequest`**:
```java
record SendSupportMessageRequest(@NotBlank @Size(max = 2000) String text)
```

**`UpdateTicketStatusRequest`**:
```java
record UpdateTicketStatusRequest(SupportTicketStatus status)
```

**`SupportOperatorNotification`** (WS payload):
```java
record SupportOperatorNotification(
    Long ticketId, Long userId, String userFirstName,
    SupportMessageResponse message
)
```

---

### Новые компоненты бэкенда

| Компонент | Пакет |
|---|---|
| `SupportTicket.java` | `model/entity` |
| `SupportMessage.java` | `model/entity` |
| `SupportTicketRepository.java` | `repository` |
| `SupportMessageRepository.java` | `repository` |
| `SupportService.java` | `service` |
| `SupportController.java` | `web/controller` (пользовательские эндпоинты) |
| `AdminSupportController.java` | `web/controller` (оперативные эндпоинты под `/api/admin`) |

---

## UI Requirements

### Фронтенд (React 19, `/opt/join/front/`)

#### Новая страница: `/support` — SupportScreen

**Роутинг:** добавить route `/support` в `App.tsx` с `MainLayout`.

**Состояния экрана:**

| Состояние | Поведение |
|---|---|
| `loading` | Skeleton-заглушка: 3-4 "пузыря" сообщений |
| `empty` | Иллюстрация + текст «Напишите нам — мы ответим» + кнопка «Новое обращение» |
| `active` | Список сообщений + поле ввода снизу |
| `error` | Toast-ошибка, кнопка «Повторить» |
| `sending` | Кнопка отправки задизейблена, spinner |

**Компоненты:**
- `SupportScreen.tsx` — основной экран
- `SupportMessageBubble.tsx` — пузырь сообщения (prop: `senderType: 'USER' | 'OPERATOR'`)
- `SupportInputBar.tsx` — textarea + кнопка «Отправить»

**Поведение:**
- При входе на экран: GET /api/support/ticket (создать/получить тикет) → GET messages
- Подписка на WebSocket `/user/queue/support` после получения тикета
- При получении нового сообщения через WS — добавить в конец списка, скролл вниз
- При открытии экрана — PUT /api/support/ticket/read
- Infinite scroll вверх: при достижении top загрузить `page+1`
- Сообщения пользователя — справа, синие; оператора — слева, серые
- Время отображается у каждого сообщения (HH:mm)

**Навигация:** Добавить иконку/кнопку «Поддержка» в bottom tab bar или в профиль.

---

### Админка (`/opt/join/admin/`)

Текущая архитектура админки — один монолитный `App.tsx` с конфигурационным массивом `ENTITY_CONFIGS`. Чат поддержки требует **интерактивного UI** (двухпанельный layout: список тикетов + переписка), что выходит за рамки существующего generic CRUD-интерфейса.

**Решение:** Добавить отдельный раздел «Support» в навигацию, который рендерит кастомный компонент `SupportPanel` вместо generic `EntityView`.

#### Компонент `SupportPanel.tsx`

**Layout:** Двухпанельный (split view):
- **Левая панель** — список тикетов (`SupportTicketList`)
- **Правая панель** — переписка выбранного тикета (`SupportTicketChat`)

**SupportTicketList:**
- Список тикетов, отсортированных по `lastMessageAt DESC`
- Каждый элемент: аватар-инициал, имя пользователя, Telegram ID, превью последнего сообщения, время, бейдж unread count (красный)
- Кнопка закрытия/открытия тикета (CLOSED/OPEN toggle)
- Реал-тайм обновление: подписка на WebSocket `/topic/support/operator`; при получении нового сообщения — обновить тикет в списке (счётчик + превью), переместить наверх

**SupportTicketChat:**
- Заголовок: имя + Telegram ID пользователя, статус тикета
- Сообщения: USER — справа, OPERATOR — слева (или обратно, на усмотрение)
- При выборе тикета: PUT /api/admin/support/tickets/{id}/read
- Поле ввода + кнопка «Ответить»
- При отправке: POST /api/admin/support/tickets/{id}/messages

**Состояния:**
| Состояние | Поведение |
|---|---|
| `loading` | Spinner в правой панели |
| `empty (no tickets)` | «Нет обращений» в левой панели |
| `no ticket selected` | «Выберите обращение» в правой панели |
| `sending` | Кнопка задизейблена |

**WebSocket для Adminки:**  
Операторский WebSocket-клиент подключается к `/ws` с Basic Auth заголовком. Подписывается на `/topic/support/operator`. При получении уведомления:
1. Обновить тикет в списке (lastMessage, unreadCount++)
2. Если тикет открыт прямо сейчас — добавить сообщение в чат (без повторного запроса)

---

## Sequence Diagrams

### Пользователь отправляет первое сообщение

```
User (TG) → Frontend → GET /api/support/ticket (create if not exists)
Frontend ← Backend: SupportTicketResponse {id: 1, status: OPEN}
User types message
Frontend → POST /api/support/ticket/messages {text: "..."}
Backend → save SupportMessage(senderType=USER)
Backend → update support_tickets.last_message_at
Backend → SimpMessagingTemplate.convertAndSend("/topic/support/operator", notification)
Frontend ← Backend: SupportMessageResponse
Operator Admin ← WebSocket: SupportOperatorNotification
```

### Оператор отвечает

```
Operator → POST /api/admin/support/tickets/1/messages {text: "..."}
Backend → save SupportMessage(senderType=OPERATOR)
Backend → update support_tickets.last_message_at
Backend → SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/support", message)
Operator ← Backend: SupportMessageResponse
User Frontend ← WebSocket: SupportMessageResponse
```

---

## Edge Cases

| Сценарий | Ожидаемое поведение |
|---|---|
| Пользователь отправляет пустое сообщение | Валидация на фронте + `400 Bad Request` на бэке |
| Пользователь отправляет сообщение длиннее 2000 символов | Блокировка на фронте (counter), `400` на бэке |
| Тикет закрыт (CLOSED) — пользователь пытается написать | Пользователю разрешено — создаётся новый тикет ИЛИ показывается сообщение «Обращение закрыто, открыть новое?» (уточнить у директора) |
| Тикет закрыт (CLOSED) — оператор пытается ответить | `409 Conflict` с сообщением «Тикет закрыт» |
| WebSocket отключился | Автоматический reconnect (SockJS встроен); показать индикатор «Нет соединения» |
| Пользователь удалён из системы | В списке тикетов показывать `[Deleted User]` + сохранить telegramId для идентификации |
| Два оператора одновременно смотрят тикет | Оба видят сообщения в реальном времени, оба могут отвечать. Конфликт не блокируется (append-only модель) |
| Очень длинная история (1000+ сообщений) | Pageable (page/size) на GET messages; фронт грузит постранично при scroll-up |
| Нет ответа от бэкенда (таймаут) | Toast на фронте «Не удалось отправить, попробуйте снова»; сообщение не теряется (хранится в state) |
| Пользователь с отключённым JS/TG SDK (dev mode) | Существующий паттерн: try/catch в main.tsx; поддержка работает через обычный HTTP, WS degrade gracefully |
| Множественные вкладки / сессии пользователя | WS `convertAndSendToUser` рассылает всем сессиям пользователя — корректно, обе вкладки получат сообщение |

---

## Зависимости

### Что нужно от существующих компонентов

| Компонент | Что использует |
|---|---|
| `WebSocketConfig.java` | Уже настроен `/topic`, `/queue`, prefix `/app` — без изменений |
| `SecurityConfig.java` | `/api/support/**` — добавить в Telegram-защищённые; `/api/admin/support/**` — уже покрывается `securityMatcher("/api/admin/**")` |
| `SimpMessagingTemplate` | Уже бин в контексте (используется в `ChatService`) — инжектить в `SupportService` |
| `UserRepository` | Нужен для получения `firstName` и `telegramId` в `SupportService` |
| `TelegramAuthFilter` | Без изменений; `requireCurrentUserId()` паттерн из `ChatController` переиспользовать |

### WebSocket аутентификация для Admin

**Проблема:** STOMP handshake для оператора использует Basic Auth, но `/ws/**` сейчас `permitAll`. WebSocket-соединение от SockJS — это HTTP upgrade, поэтому Basic Auth header передаётся на handshake.

**Решение:** В WebSocket STOMP-фрейме передавать Authorization header как STOMP connect header. Либо упростить — оператор не подключается через STOMP, а использует **polling** (GET раз в 5 секунд) для списка тикетов в MVP. 

**Рекомендация для MVP:** Оператор в Admin использует polling (setInterval, 5s) вместо STOMP — проще, не требует изменений в Security. STOMP для пользователя — остаётся как есть.

### Что может сломать

- **Liquibase:** новые файлы `012` и `013` — убедиться, что `db.changelog-master.yaml` включает их (обычно через `include file`)
- **Admin `App.tsx`:** добавление нового раздела не ломает существующие ENTITY_CONFIGS, но требует рефакторинга рендеринга (условный рендер `SupportPanel` vs generic view)

---

## Scope MVP vs Future

### MVP (эта спецификация)
- Один тикет на пользователя (UNIQUE user_id)
- Текстовые сообщения
- Базовые статусы: OPEN / CLOSED
- Real-time для пользователя (WS), polling для оператора в Admin

### Future (вне скопа)
- Вложения (изображения)
- Несколько тикетов от одного пользователя (категории: баг, вопрос, предложение)
- Назначение тикета конкретному оператору
- SLA / автоответ
- Уведомления в Telegram (бот → пользователю при ответе оператора)
- Full-duplex WS для оператора в Admin

---

## Контрольный список для разработчика

- [ ] Создать Liquibase migrations `012`, `013`
- [ ] Создать entity `SupportTicket`, `SupportMessage` и enum `SupportTicketStatus`, `SupportSenderType`
- [ ] Создать `SupportTicketRepository`, `SupportMessageRepository`
- [ ] Создать `SupportService` с методами: `getOrCreateTicket`, `getMessages`, `sendUserMessage`, `sendOperatorMessage`, `markReadByUser`, `markReadByOperator`, `updateStatus`, `getAllTickets`
- [ ] Создать `SupportController` (`/api/support/**`, Telegram auth)
- [ ] Создать `AdminSupportController` (`/api/admin/support/**`, Basic auth — автоматически через SecurityConfig)
- [ ] Добавить `/api/support/**` в список защищённых Telegram-эндпоинтов в `SecurityConfig`
- [ ] Инжектировать `SimpMessagingTemplate` в `SupportService`, слать события пользователю при ответе оператора
- [ ] Фронт: создать `SupportScreen.tsx`, подключить к роутингу `/support`
- [ ] Фронт: WS подписка на `/user/queue/support`
- [ ] Фронт: добавить точку входа в Support (tab bar / профиль)
- [ ] Admin: добавить раздел «Support» с компонентом `SupportPanel.tsx`
- [ ] Admin: polling 5s для обновления списка тикетов
