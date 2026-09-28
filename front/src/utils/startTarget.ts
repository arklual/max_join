/**
 * Deep links into the mini app via MAX `startapp` / bot `open_app` payloads.
 *
 *   chat_<id>   → personal chat (bot notifications about matches and messages)
 *   gchat_<id>  → group chat
 *   event_<id>  → event page (shared events, event reminders)
 *   group_<id>  → invitation into an event group
 *   matches     → found companions and "пойдём вместе?" invitations (chats list)
 *   pushkin     → afisha filtered to Pushkin card events
 *
 * Friend-group invites (`join_<code>`) are handled separately in inviteLinks.ts.
 */

export const START_PARAM = {
  chat: 'chat_',
  groupChat: 'gchat_',
  event: 'event_',
  group: 'group_',
  pushkin: 'pushkin',
  matches: 'matches',
} as const;

const FILTERS_KEY = 'afisha_filters';
const PENDING_ROUTE_KEY = 'join.pendingRoute';

export function buildEventStartLink(eventId: number | string, botUsername: string): string {
  return `https://max.ru/${botUsername}?startapp=${START_PARAM.event}${eventId}`;
}

/** Shareable invitation into an event group; falls back to an in-browser URL without a bot. */
export function buildGroupInviteLink(groupId: number | string, botUsername: string): string {
  return botUsername
    ? `https://max.ru/${botUsername}?startapp=${START_PARAM.group}${groupId}`
    : `${window.location.origin}/groups/${groupId}`;
}

/** Keeps a deep-link route across the registration detour (Splash → /register). */
export function rememberPendingRoute(route: string): void {
  try {
    sessionStorage.setItem(PENDING_ROUTE_KEY, route);
  } catch {
    // storage unavailable — the user just lands on the afisha
  }
}

export function takePendingRoute(): string | null {
  try {
    const route = sessionStorage.getItem(PENDING_ROUTE_KEY);
    sessionStorage.removeItem(PENDING_ROUTE_KEY);
    return route;
  } catch {
    return null;
  }
}

function idAfter(raw: string, prefix: string): string | null {
  if (!raw.startsWith(prefix)) return null;
  const id = raw.slice(prefix.length);
  return /^\d{1,18}$/.test(id) ? id : null;
}

/** Maps a raw start parameter to an in-app route, or null if it isn't a known deep link. */
export function routeForStartParam(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const value = raw.trim();
  let id = idAfter(value, START_PARAM.groupChat);
  if (id) return `/group-chats/${id}`;
  id = idAfter(value, START_PARAM.chat);
  if (id) return `/chats/${id}`;
  id = idAfter(value, START_PARAM.event);
  if (id) return `/events/${id}`;
  id = idAfter(value, START_PARAM.group);
  if (id) return `/groups/${id}`;
  if (value === START_PARAM.matches) return '/chats';
  if (value === START_PARAM.pushkin) {
    try {
      const stored = JSON.parse(sessionStorage.getItem(FILTERS_KEY) || '{}');
      sessionStorage.setItem(FILTERS_KEY, JSON.stringify({ ...stored, pushkinCard: true }));
    } catch {
      // storage unavailable — the afisha simply opens unfiltered
    }
    return '/afisha';
  }
  return null;
}
