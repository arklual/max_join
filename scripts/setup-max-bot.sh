#!/bin/bash
# Скрипт настройки MAX бота для JOIN (использует MAX Bot API напрямую через curl)
#
# Что делает:
# 1. Проверяет токен бота (GET /me)
# 2. Устанавливает команды бота (/start, /help)
# 3. Подписывает бота на webhook (если задан WEBHOOK_URL)
#
# Бэкенд делает шаги 2–3 сам при старте; скрипт нужен для ручной настройки.
# URL мини-приложения привязывается к боту в кабинете https://business.max.ru —
# через Bot API это не настраивается.
#
# Использование: ./setup-max-bot.sh [BOT_TOKEN] [WEBHOOK_URL]
# Или задать через переменные окружения MAX_BOT_TOKEN, MAX_WEBHOOK_URL, MAX_WEBHOOK_SECRET

set -euo pipefail

BOT_TOKEN="${1:-${MAX_BOT_TOKEN:-}}"
WEBHOOK_URL="${2:-${MAX_WEBHOOK_URL:-}}"
WEBHOOK_SECRET="${MAX_WEBHOOK_SECRET:-}"
API="${MAX_API_URL:-https://platform-api.max.ru}"

if [ -z "$BOT_TOKEN" ]; then
    echo "❌ Ошибка: не указан BOT_TOKEN"
    echo "Использование: $0 <BOT_TOKEN> [WEBHOOK_URL]"
    echo "Или: MAX_BOT_TOKEN=... $0"
    exit 1
fi

api() {
    local method="$1" path="$2" body="${3:-}"
    if [ -n "$body" ]; then
        curl -sf -X "$method" "${API}${path}" -H "Authorization: ${BOT_TOKEN}" \
            -H "Content-Type: application/json" -d "$body"
    else
        curl -sf -X "$method" "${API}${path}" -H "Authorization: ${BOT_TOKEN}"
    fi
}

echo "🤖 Настройка MAX бота для JOIN"
echo ""

echo "1️⃣  Проверяю бота..."
BOT_INFO=$(api GET /me)
BOT_NAME=$(echo "$BOT_INFO" | grep -o '"first_name":"[^"]*"' | cut -d'"' -f4)
BOT_USERNAME=$(echo "$BOT_INFO" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
echo "   ✅ Бот: ${BOT_NAME} (@${BOT_USERNAME})"
echo "   Ссылка на бота:        https://max.ru/${BOT_USERNAME}"
echo "   Ссылка на мини-апп:    https://max.ru/${BOT_USERNAME}?startapp"
echo ""

echo "2️⃣  Устанавливаю команды бота..."
api PATCH /me/commands '{"commands":[
    {"name":"start","description":"Запустить приложение JOIN"},
    {"name":"help","description":"Помощь и информация"}
]}' >/dev/null
echo "   ✅ Команды установлены: /start, /help"
echo ""

if [ -n "$WEBHOOK_URL" ]; then
    echo "3️⃣  Подписываю бота на webhook..."
    api POST /subscriptions "{
        \"url\": \"${WEBHOOK_URL}/api/max/webhook\",
        \"update_types\": [\"bot_started\", \"message_created\"],
        \"secret\": \"${WEBHOOK_SECRET}\"
    }" >/dev/null
    echo "   ✅ Webhook: ${WEBHOOK_URL}/api/max/webhook"
else
    echo "3️⃣  WEBHOOK_URL не задан — пропускаю подписку на webhook"
fi
echo ""
echo "🎉 Готово! Не забудьте указать URL мини-приложения в кабинете MAX для партнёров."
