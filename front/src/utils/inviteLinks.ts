/**
 * Helpers for building/parsing friend-group invite deep links.
 *
 * The canonical shareable link is a MAX deep link:
 *   {@code https://max.ru/<botUsername>?startapp=join_<inviteCode>}
 * which (a) any camera app can scan and opens MAX → our Mini App,
 * (b) inside MAX passes {@code join_<inviteCode>} as {@code start_param},
 * which our SplashScreen parses to auto-open the join dialog.
 *
 * We still accept legacy {@code ${origin}/friend-groups?invite=<code>} URLs
 * (older QR codes already in the wild) for backwards compatibility.
 */

export const INVITE_START_PARAM_PREFIX = 'join_';

export function buildFriendGroupInviteUrl(
  inviteCode: string,
  botUsername: string | null,
  messenger: 'max' | 'telegram' = 'max',
): string {
  if (botUsername && botUsername.trim()) {
    // Telegram: /start join_<code> — the bot replies with a button that opens the mini app.
    return messenger === 'telegram'
      ? `https://t.me/${botUsername}?start=${INVITE_START_PARAM_PREFIX}${inviteCode}`
      : `https://max.ru/${botUsername}?startapp=${INVITE_START_PARAM_PREFIX}${inviteCode}`;
  }
  // Fallback (e.g. local dev without bot configured) — still works in-browser.
  return `${window.location.origin}/friend-groups?invite=${inviteCode}`;
}

/**
 * Try to extract a friend-group invite code from arbitrary scanned text.
 * Accepts:
 *   - "join_<code>"                                 (raw start_param)
 *   - "https://max.ru/<bot>?startapp=join_<code>"   (canonical deep link)
 *   - "https://max.ru/<bot>?start=join_<code>"      (bot start link)
 *   - "https://anywhere/friend-groups?invite=<code>" (legacy)
 *   - "<code>" (just the code, if it looks invite-ish)
 */
export function extractInviteCode(input: string): string | null {
  if (!input) return null;
  const text = input.trim();
  if (!text) return null;

  // Raw start_param form.
  if (text.startsWith(INVITE_START_PARAM_PREFIX)) {
    const code = text.slice(INVITE_START_PARAM_PREFIX.length);
    return sanitizeCode(code);
  }

  // Try URL parsing.
  try {
    const url = new URL(text);
    const startapp = url.searchParams.get('startapp') ?? url.searchParams.get('start');
    if (startapp && startapp.startsWith(INVITE_START_PARAM_PREFIX)) {
      return sanitizeCode(startapp.slice(INVITE_START_PARAM_PREFIX.length));
    }
    const invite = url.searchParams.get('invite');
    if (invite) {
      return sanitizeCode(invite);
    }
  } catch {
    // not a URL — fall through
  }

  // Bare code.
  if (/^[A-Za-z0-9_-]{4,32}$/.test(text)) {
    return text.toLowerCase();
  }

  return null;
}

function sanitizeCode(raw: string): string | null {
  const trimmed = raw.split(/[&#?]/)[0]?.trim() ?? '';
  if (!trimmed) return null;
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(trimmed)) return null;
  return trimmed.toLowerCase();
}

const PENDING_INVITE_KEY = 'join.pendingInvite';

/** Remembers an invite code across the registration detour (Splash → /register). */
export function rememberPendingInvite(code: string): void {
  try {
    sessionStorage.setItem(PENDING_INVITE_KEY, code);
  } catch {
    // storage unavailable — the invite just won't survive registration
  }
}

/** Returns and clears the remembered invite code, if any. */
export function takePendingInvite(): string | null {
  try {
    const code = sessionStorage.getItem(PENDING_INVITE_KEY);
    sessionStorage.removeItem(PENDING_INVITE_KEY);
    return code;
  } catch {
    return null;
  }
}
