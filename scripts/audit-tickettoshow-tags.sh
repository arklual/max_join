#!/usr/bin/env bash
set -u

BACKEND_URL="${BACKEND_URL:-https://localhost}"
ADMIN_AUTH="${ADMIN_AUTH:-admin:change_me}"
TTS_API_URL="${TTS_API_URL:-https://api.tickettoshow.ru/api}"
WORKDIR="${WORKDIR:-/tmp/tickettoshow-audit}"

mkdir -p "$WORKDIR"

pass_count=0
fail_count=0
warn_count=0

log() {
  printf '\n## %s\n' "$1"
}

ok() {
  pass_count=$((pass_count + 1))
  printf 'PASS %s\n' "$1"
}

warn() {
  warn_count=$((warn_count + 1))
  printf 'WARN %s\n' "$1"
}

fail() {
  fail_count=$((fail_count + 1))
  printf 'FAIL %s\n' "$1"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Required command not found: $1"
    exit 1
  fi
}

admin_curl() {
  curl --noproxy '*' -k -sS --connect-timeout 10 --max-time 240 -u "$ADMIN_AUTH" "$@"
}

telegram_curl_from_back() {
  local path="$1"
  docker exec join-back sh -lc '
    path="$1"
    user_json="{\"id\":987654321,\"first_name\":\"Smoke\",\"username\":\"smoke\"}"
    user_enc="%7B%22id%22%3A987654321%2C%22first_name%22%3A%22Smoke%22%2C%22username%22%3A%22smoke%22%7D"
    auth_date=1770000000
    data="auth_date=$auth_date
user=$user_json"
    secret_hex=$(printf "%s" "$TELEGRAM_BOT_TOKEN" | openssl dgst -sha256 -mac HMAC -macopt key:WebAppData -binary | od -An -tx1 | tr -d " \n")
    hash=$(printf "%s" "$data" | openssl dgst -sha256 -mac HMAC -macopt hexkey:$secret_hex | awk "{print \$2}")
    init_data="user=$user_enc&auth_date=$auth_date&hash=$hash"
    curl -sS -H "X-Telegram-Init-Data: $init_data" "http://localhost:8080$path"
  ' sh "$path"
}

require_cmd curl
require_cmd jq
require_cmd docker

log "External Tickettoshow categories"
p_list_json="$WORKDIR/p_list.json"
curl -sS --connect-timeout 10 --max-time 30 -H 'User-Agent: insomnia/12.2.0' "$TTS_API_URL/p_list" -o "$p_list_json"

if jq -e '.message == "OK" and (.data | type == "object")' "$p_list_json" >/dev/null; then
  ok "Tickettoshow /p_list returned object data with OK message"
else
  fail "Tickettoshow /p_list shape is unexpected"
fi

jq -r '
  .data
  | to_entries[]
  | .key as $category
  | .value as $items
  | [
      $category,
      ($items | length),
      ([$items[]? | select(has("performance_id") and has("show_name"))] | length),
      (if ($items | length) > 0 then ($items[0] | keys_unsorted | join(",")) else "" end)
    ]
  | @tsv
' "$p_list_json" | while IFS=$'\t' read -r category total performances keys; do
  printf 'category=%s total=%s performanceLike=%s keys=%s\n' "$category" "$total" "$performances" "$keys"
  if [ "$category" = "Театры" ] && [ "$performances" != "0" ]; then
    fail "Театры category unexpectedly contains performance-like items"
  fi
done

log "Parser run"
run_json="$WORKDIR/parser_run.json"
admin_curl \
  -H 'Content-Type: application/json' \
  -X POST "$BACKEND_URL/api/admin/parser/run" \
  -d '{"sources":["TICKETTOSHOW"]}' >"$run_json"

run_id="$(jq -r '.runId // empty' "$run_json")"
if [ -n "$run_id" ]; then
  ok "Parser run completed with runId=$run_id"
else
  fail "Parser run did not return runId"
fi

latest_run_json="$WORKDIR/latest_run.json"
admin_curl "$BACKEND_URL/api/admin/parser/runs" | jq 'map(select(.source == "TICKETTOSHOW")) | .[0]' >"$latest_run_json"
cat "$latest_run_json"
printf '\n'

if jq -e '.status == "SUCCESS" and .eventsFound > 0 and .eventsSkipped == 0' "$latest_run_json" >/dev/null; then
  ok "Latest Tickettoshow run succeeded without skipped events"
else
  fail "Latest Tickettoshow run has failures or skipped events"
fi

log "Database canonical tag invariants"
missing_canonical="$WORKDIR/missing_canonical.tsv"
docker exec join-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F $'\''\t'\'' -c "
  select e.id, e.title, e.type
  from events e
  where e.source = '\''TICKETTOSHOW'\''
    and not exists (
      select 1
      from event_tags et
      join tags t on t.id = et.tag_id
      where et.event_id = e.id
        and t.slug = case e.type
          when '\''THEATER'\'' then '\''theater'\''
          when '\''MUSIC'\'' then '\''music'\''
          when '\''ART'\'' then '\''art'\''
          when '\''CINEMA'\'' then '\''cinema'\''
          when '\''FESTIVAL'\'' then '\''festival'\''
          when '\''MASTER_CLASS'\'' then '\''master-class'\''
          when '\''EXCURSION'\'' then '\''excursion'\''
          when '\''CAREER'\'' then '\''career'\''
          when '\''SPORT'\'' then '\''sport'\''
        end
    )
  order by e.event_date, e.id;
"' >"$missing_canonical"

if [ -s "$missing_canonical" ]; then
  fail "Some Tickettoshow events are missing canonical type tags"
  cat "$missing_canonical"
else
  ok "All Tickettoshow events have canonical type tags"
fi

docker exec join-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "
  select e.type, count(distinct e.id) as events, string_agg(distinct t.slug, '\'','\'' order by t.slug) as tag_slugs
  from events e
  join event_tags et on et.event_id = e.id
  join tags t on t.id = et.tag_id
  where e.source = '\''TICKETTOSHOW'\''
  group by e.type
  order by e.type;
"'

log "Public feed and tag filters"
tts_ids_file="$WORKDIR/tts_ids.txt"
docker exec join-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "select id from events where source = '\''TICKETTOSHOW'\'' order by id;"' >"$tts_ids_file"
sort "$tts_ids_file" -o "$tts_ids_file"

all_events_json="$WORKDIR/events_all.json"
telegram_curl_from_back "/api/events?page=0&size=500&sort=eventDate,asc&sort=eventTime,asc" >"$all_events_json"

feed_ids_file="$WORKDIR/feed_ids.txt"
jq -r '.content[].id' "$all_events_json" | sort >"$feed_ids_file"
tts_all_count="$(comm -12 "$tts_ids_file" "$feed_ids_file" | wc -l | tr -d ' ')"
tts_db_count="$(wc -l <"$tts_ids_file" | tr -d ' ')"
if [ "$tts_all_count" = "$tts_db_count" ]; then
  ok "Regular feed contains all $tts_all_count Tickettoshow events"
else
  fail "Regular feed contains $tts_all_count Tickettoshow events, DB has $tts_db_count"
fi

check_filter() {
  local tag_id="$1"
  local tag_name="$2"
  local expected_slug="$3"
  local output="$WORKDIR/events_tag_${tag_id}.json"
  local expected_count
  local actual_count
  local expected_ids_file
  local actual_ids_file
  local matched_ids_file

  expected_ids_file="$WORKDIR/expected_tag_${tag_id}_ids.txt"
  actual_ids_file="$WORKDIR/actual_tag_${tag_id}_ids.txt"
  matched_ids_file="$WORKDIR/matched_tag_${tag_id}_ids.txt"
  expected_count="$(docker exec join-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "
    select count(distinct e.id)
    from events e
    join event_tags et on et.event_id = e.id
    join tags t on t.id = et.tag_id
    where e.source = '\''TICKETTOSHOW'\'' and t.slug = '\'''"$expected_slug"''\'';
  "')"
  docker exec join-postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "
    select distinct e.id
    from events e
    join event_tags et on et.event_id = e.id
    join tags t on t.id = et.tag_id
    where e.source = '\''TICKETTOSHOW'\'' and t.slug = '\'''"$expected_slug"''\''
    order by e.id;
  "' >"$expected_ids_file"
  sort "$expected_ids_file" -o "$expected_ids_file"
  telegram_curl_from_back "/api/events?tagIds=$tag_id&page=0&size=500&sort=eventDate,asc&sort=eventTime,asc" >"$output"
  jq -r '.content[].id' "$output" | sort >"$actual_ids_file"
  comm -12 "$expected_ids_file" "$actual_ids_file" >"$matched_ids_file"
  actual_count="$(wc -l <"$matched_ids_file" | tr -d ' ')"

  if [ "$actual_count" = "$expected_count" ]; then
    ok "$tag_name filter contains $actual_count Tickettoshow events"
  else
    fail "$tag_name filter contains $actual_count Tickettoshow events, expected $expected_count"
    printf 'Missing ids:\n'
    comm -23 "$expected_ids_file" "$actual_ids_file"
  fi
}

check_filter 2 "Театр" "theater"
check_filter 4 "Музыка" "music"
check_filter 28 "Спектакли" "спектакли"
check_filter 29 "Спектакль" "спектакль"
check_filter 26 "Концерты" "концерты"
check_filter 27 "Концерт" "концерт"
check_filter 30 "Классика" "классика"

log "Specific regression examples"
theater_json="$WORKDIR/events_tag_2.json"
if jq -e '.content[] | select(.title == "Кыся")' "$theater_json" >/dev/null; then
  ok "Кыся is present in Театр filter"
else
  fail "Кыся is missing from Театр filter"
fi

music_json="$WORKDIR/events_tag_4.json"
if jq -e '.content[] | select(.title == "Владимир Спиваков и Хибла Герзмава")' "$music_json" >/dev/null; then
  ok "Владимир Спиваков и Хибла Герзмава is present in Музыка filter"
else
  fail "Владимир Спиваков и Хибла Герзмава is missing from Музыка filter"
fi

log "Summary"
printf 'PASS=%s WARN=%s FAIL=%s\n' "$pass_count" "$warn_count" "$fail_count"

if [ "$fail_count" -gt 0 ]; then
  exit 1
fi
