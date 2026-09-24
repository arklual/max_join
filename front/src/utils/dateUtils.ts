/**
 * Parses a backend timestamp. The server stores LocalDateTime in UTC and
 * serialises it without an offset ("2026-09-23T22:55:06.6"); a bare ISO string
 * would be read as *local* time by the browser, shifting it by the user's UTC
 * offset. Strings that already carry "Z" or "+hh:mm" are left untouched.
 */
export function parseServerDate(dateString: string): Date {
  const hasZone = /(Z|[+-]\d{2}:?\d{2})$/.test(dateString);
  const isDateTime = dateString.includes('T');
  return new Date(isDateTime && !hasZone ? `${dateString}Z` : dateString);
}

export function formatTime(dateString: string): string {
  const date = parseServerDate(dateString);
  return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

export function formatDateSeparator(dateString: string): string {
  const date = parseServerDate(dateString);
  const now = new Date();

  const isToday =
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear();

  if (isToday) return 'Сегодня';

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const isYesterday =
    date.getDate() === yesterday.getDate() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getFullYear() === yesterday.getFullYear();

  if (isYesterday) return 'Вчера';

  return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function getDateKey(dateString: string): string {
  const date = parseServerDate(dateString);
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

export function formatMessageTime(dateString: string): string {
  const date = parseServerDate(dateString);
  const now = new Date();
  const isToday =
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear();

  if (isToday) {
    return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  }

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const isYesterday =
    date.getDate() === yesterday.getDate() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getFullYear() === yesterday.getFullYear();

  if (isYesterday) return 'Вчера';

  return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

/**
 * Formats an event's date ("2026-09-26") and optional time ("19:00:00") for
 * display: "26 сентября, 19:00" (year only when it differs from the current one).
 * Falls back to the raw values if they can't be parsed.
 */
export function formatEventDateTime(
  eventDate: string | null | undefined,
  eventTime?: string | null,
  month: 'long' | 'short' = 'long',
): string {
  if (!eventDate) return '';
  const [y, m, d] = eventDate.split('-').map(Number);
  if (!y || !m || !d) return [eventDate, eventTime].filter(Boolean).join(' ');
  const date = new Date(y, m - 1, d);
  const sameYear = y === new Date().getFullYear();
  const datePart = date.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month,
    ...(sameYear ? {} : { year: 'numeric' }),
  });
  const timePart = eventTime ? eventTime.slice(0, 5) : '';
  return timePart && timePart !== '00:00' ? `${datePart}, ${timePart}` : datePart;
}
