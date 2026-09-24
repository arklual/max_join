#!/usr/bin/env bash
set -u

TTS_API_URL="${TTS_API_URL:-https://api.tickettoshow.ru/api}"
BACKEND_URL="${BACKEND_URL:-}"
ADMIN_AUTH="${ADMIN_AUTH:-admin:change_me}"
REF_CODE="${TICKETTOSHOW_REF_CODE:-gAAAAABp_MhvvV3sf7YjfILZk4uXfZu_5pIozTIc0Z7JuqPs8ZRZ9yjuRI1bmS_wl9-RA_tUpj8OzLB-pyk-BSlPZ7qf5S93ZTjCfND_S2BWq_cD5_L4tak=}"
UTM_QUERY="${TICKETTOSHOW_UTM_QUERY:-utm_source=join&utm_medium=partner&utm_campaign=tickettoshow}"
WORKDIR="${WORKDIR:-/tmp/tickettoshow-manual}"

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

curl_json() {
  local url="$1"
  local output="$2"
  local status_file="$3"
  curl -sS \
    --connect-timeout 10 \
    --max-time 30 \
    -H 'User-Agent: insomnia/12.2.0' \
    -w '%{http_code}' \
    -o "$output" \
    "$url" >"$status_file"
}

backend_curl() {
  curl --noproxy '*' -k -sS --connect-timeout 10 "$@"
}

admin_curl() {
  backend_curl -u "$ADMIN_AUTH" "$@"
}

assert_http_200() {
  local status="$1"
  local label="$2"
  if [ "$status" = "200" ]; then
    ok "$label returned HTTP 200"
  else
    fail "$label returned HTTP $status"
  fi
}

require_cmd curl
require_cmd jq

log "External Tickettoshow /p_list"
p_list_json="$WORKDIR/p_list.json"
p_list_status="$WORKDIR/p_list.status"
curl_json "$TTS_API_URL/p_list" "$p_list_json" "$p_list_status"
p_list_http="$(cat "$p_list_status")"
assert_http_200 "$p_list_http" "GET $TTS_API_URL/p_list"

message="$(jq -r '.message // empty' "$p_list_json" 2>/dev/null)"
if [ "$message" = "OK" ]; then
  ok "/p_list message is OK"
else
  fail "/p_list message is '$message'"
fi

for category in "Концерты" "Спектакли" "Классика" "Детям" "Театры"; do
  if jq -e --arg category "$category" '.data[$category] | type == "array"' "$p_list_json" >/dev/null; then
    ok "/p_list has category $category"
  else
    fail "/p_list missing category $category"
  fi
done

event_count="$(jq '[.data | to_entries[] | select(.value | type == "array") | .value[] | select(has("performance_id"))] | length' "$p_list_json")"
if [ "$event_count" -gt 0 ]; then
  ok "/p_list has $event_count performance events"
else
  fail "/p_list has no performance events"
fi

theatres_as_events="$(jq '[.data["Театры"][]? | select(has("performance_id"))] | length' "$p_list_json")"
if [ "$theatres_as_events" -eq 0 ]; then
  ok "Театры category contains no performance_id entries"
else
  fail "Театры category unexpectedly contains performance_id entries"
fi

log "Sample list fields"
jq -r '
  .data
  | to_entries[]
  | select(.value | type == "array")
  | .key as $category
  | .value[]
  | select(has("performance_id"))
  | [$category, .performance_id, .show_name, .date_time, (.min_price | tostring), .address]
  | @tsv
' "$p_list_json" | head -12

log "External Tickettoshow /card_p details"
mapfile -t detail_targets < <(
  jq -r '
    def first_id($category):
      .data[$category][]? | select(has("performance_id")) | .performance_id;
    [
      ("Спектакли:" + (first_id("Спектакли") // "")),
      ("Концерты:" + (first_id("Концерты") // "")),
      ("Классика:" + (first_id("Классика") // "")),
      ("Цена0:" + ((.data | to_entries[] | select(.value | type == "array") | .value[] | select(has("performance_id") and (.min_price == 0)) | .performance_id) // ""))
    ][]
  ' "$p_list_json" | awk -F: '$2 != "" && !seen[$2]++'
)

for target in "${detail_targets[@]}"; do
  label="${target%%:*}"
  performance_id="${target#*:}"
  detail_json="$WORKDIR/card_${performance_id}.json"
  detail_status="$WORKDIR/card_${performance_id}.status"
  curl_json "$TTS_API_URL/card_p?performance_id=$performance_id" "$detail_json" "$detail_status"
  detail_http="$(cat "$detail_status")"
  assert_http_200 "$detail_http" "GET /card_p?performance_id=$performance_id ($label)"

  detail_message="$(jq -r '.message // empty' "$detail_json" 2>/dev/null)"
  detail_id="$(jq -r '.data.performance_id // empty' "$detail_json" 2>/dev/null)"
  title="$(jq -r '.data.show_name // empty' "$detail_json" 2>/dev/null)"
  begin_time="$(jq -r '.data.date_time[0].begin_time // .data.date_time // empty' "$detail_json" 2>/dev/null)"
  image="$(jq -r '.data.img // empty' "$detail_json" 2>/dev/null)"
  price="$(jq -r '.data.min_price // .data.date_time[0].min_price // empty' "$detail_json" 2>/dev/null)"
  description_len="$(jq -r '(.data.show_description // "") | length' "$detail_json" 2>/dev/null)"

  [ "$detail_message" = "OK" ] && ok "$performance_id detail message is OK" || fail "$performance_id detail message is '$detail_message'"
  [ "$detail_id" = "$performance_id" ] && ok "$performance_id detail id matches" || fail "$performance_id detail id mismatch: '$detail_id'"
  [ -n "$title" ] && ok "$performance_id has title: $title" || fail "$performance_id has no title"
  [ -n "$begin_time" ] && ok "$performance_id has begin_time: $begin_time" || fail "$performance_id has no begin_time"
  [ -n "$image" ] && ok "$performance_id has image path" || fail "$performance_id has no image"
  [ -n "$price" ] && ok "$performance_id has price: $price" || warn "$performance_id has no price"
  [ "$description_len" -gt 0 ] && ok "$performance_id has description" || warn "$performance_id has empty description"

  printf '%s\t%s\t%s\t%s\t%s\n' "$label" "$performance_id" "$title" "$begin_time" "$price"
done

log "Negative external API cases"
for url in "$TTS_API_URL/card_p" "$TTS_API_URL/card_p?performance_id=not-a-number" "$TTS_API_URL/card_p?performance_id=999999999"; do
  name="$(printf '%s' "$url" | sed 's#[^a-zA-Z0-9]#_#g')"
  output="$WORKDIR/negative_${name}.json"
  status_file="$WORKDIR/negative_${name}.status"
  curl_json "$url" "$output" "$status_file"
  status="$(cat "$status_file")"
  if [ "$status" = "000" ]; then
    fail "Negative case $url did not receive HTTP response"
  else
    ok "Negative case $url returned HTTP $status"
    printf 'body preview: '
    head -c 180 "$output"
    printf '\n'
  fi
done

log "Referral page"
first_performance_id="$(jq -r '.data | to_entries[] | select(.value | type == "array") | .value[] | select(has("performance_id")) | .performance_id' "$p_list_json" | head -1)"
ref_url="https://tickettoshow.ru/concert?concert_id=$first_performance_id&ref=$REF_CODE&$UTM_QUERY"
ref_status="$(curl -sS -I --connect-timeout 10 --max-time 30 -w '%{http_code}' -o "$WORKDIR/referral_headers.txt" "$ref_url")"
if [ "$ref_status" = "200" ] || [ "$ref_status" = "301" ] || [ "$ref_status" = "302" ]; then
  ok "Referral URL returned HTTP $ref_status"
else
  warn "Referral URL returned HTTP $ref_status"
fi
printf 'Referral URL: %s\n' "$ref_url"

if [ -n "$BACKEND_URL" ]; then
  log "Local backend parser run"
  parser_run_json="$WORKDIR/backend_parser_run.json"
  parser_run_status="$WORKDIR/backend_parser_run.status"
  admin_curl \
    --max-time 180 \
    -H 'Content-Type: application/json' \
    -w '%{http_code}' \
    -o "$parser_run_json" \
    -X POST "$BACKEND_URL/api/admin/parser/run" \
    -d '{"sources":["TICKETTOSHOW"]}' >"$parser_run_status"
  backend_status="$(cat "$parser_run_status")"
  assert_http_200 "$backend_status" "POST $BACKEND_URL/api/admin/parser/run"
  cat "$parser_run_json"
  printf '\n'

  log "Local backend parser runs"
  admin_curl "$BACKEND_URL/api/admin/parser/runs" | tee "$WORKDIR/backend_parser_runs.json" | jq '.[0]'

  log "Local backend admin events"
  admin_curl "$BACKEND_URL/api/admin/events" -o "$WORKDIR/backend_admin_events.json"
  for query in "Райкин" "Крестный отец" "Гамлет"; do
    found="$(jq --arg query "$query" '[.[] | select((.title // "") | contains($query))] | length' "$WORKDIR/backend_admin_events.json")"
    if [ "$found" -gt 0 ]; then
      ok "Admin events contain '$query' ($found events)"
      jq --arg query "$query" '
        [.[] | select((.title // "") | contains($query))]
        | .[0]
        | {
            id,
            title,
            type,
            imageUrl,
            price,
            eventDate,
            eventTime,
            ticketUrl,
            city,
            source,
            externalId,
            descriptionLength: ((.description // "") | length)
          }
      ' "$WORKDIR/backend_admin_events.json"
      ticket_url="$(jq -r --arg query "$query" '[.[] | select((.title // "") | contains($query))] | .[0].ticketUrl // ""' "$WORKDIR/backend_admin_events.json")"
      if [[ "$ticket_url" == *"utm_source="* && "$ticket_url" == *"utm_medium="* && "$ticket_url" == *"utm_campaign="* ]]; then
        ok "Admin event '$query' ticketUrl contains UTM"
      else
        fail "Admin event '$query' ticketUrl missing UTM: $ticket_url"
      fi
    else
      warn "Admin events do not contain '$query'"
    fi
  done

  log "Local public API auth boundary"
  public_status="$(backend_curl -w '%{http_code}' -o "$WORKDIR/backend_public_events_unauthorized.json" "$BACKEND_URL/api/events?size=1")"
  if [ "$public_status" = "401" ] || [ "$public_status" = "403" ]; then
    ok "Public /api/events without Telegram auth is protected (HTTP $public_status)"
  else
    warn "Public /api/events without Telegram auth returned HTTP $public_status"
  fi
else
  warn "BACKEND_URL is not set; skipped local backend parser and /api/events checks"
fi

log "Summary"
printf 'PASS=%s WARN=%s FAIL=%s\n' "$pass_count" "$warn_count" "$fail_count"
printf 'Artifacts: %s\n' "$WORKDIR"

if [ "$fail_count" -gt 0 ]; then
  exit 1
fi
