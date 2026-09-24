# JOIN в MAX

JOIN перенесён с Telegram на мессенджер MAX: мини-приложение работает через
MAX Bridge, бот — через MAX Bot API.

## Что поменялось

| Было (Telegram) | Стало (MAX) |
|---|---|
| `@telegram-apps/sdk-react`, `window.Telegram.WebApp` | `https://st.max.ru/js/max-web-app.js`, `window.WebApp` (`front/src/api/maxBridge.ts`) |
| Заголовок `X-Telegram-Init-Data` | `X-Max-Init-Data` (валидация HMAC-SHA256 та же, `MaxInitDataValidator`) |
| `users.telegram_id` | `users.max_id` (миграция 040; `telegram_id` оставлен как legacy-колонка) |
| `showScanQrPopup` | `WebApp.openCodeReader()` |
| `https://t.me/<bot>?start=join_<code>` | `https://max.ru/<bot>?startapp=join_<code>` |
| `api.telegram.org` sendMessage / setMyCommands / setWebhook | `platform-api.max.ru` `POST /messages`, `PATCH /me/commands`, `POST /subscriptions` (`MaxBotApiClient`) |
| `/api/telegram/webhook` | `/api/max/webhook` (события `bot_started`, `message_created`; секрет в `X-Max-Bot-Api-Secret`) |
| Кнопка `web_app` | Кнопка `open_app` (`web_app` = username бота, `payload` → `start_param`) |
| `/auth/link-telegram` | `/auth/link-max` |
| JSON `telegramId`, `telegramBotUsername` | `maxId`, `maxBotUsername` |

Поле профиля `telegramChannel` (ссылка на Telegram-канал пользователя) —
пользовательские данные, оставлено как есть. Парсер Telegram-канала «Новой оперы»
— источник афиши, к мессенджеру не относится.

## Запуск

1. `.env`: `MAX_BOT_TOKEN`, `MAX_WEBHOOK_URL=https://<домен>`, опционально `MAX_WEBHOOK_SECRET`.
2. `docker compose up -d --build` — бэкенд при старте сам ставит команды и подписку на webhook
   (или вручную: `scripts/setup-max-bot.sh`).
3. В кабинете https://business.max.ru укажите URL мини-приложения `https://<домен>` для бота.
4. Ссылка на мини-приложение: `https://max.ru/<bot_username>?startapp`.

Мини-приложению нужен публичный HTTPS-адрес.
