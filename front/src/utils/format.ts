/**
 * "1 200 ₽", "Бесплатно" for a zero price, or '' when the price is unknown
 * (sources such as Yandex Afisha don't publish it) — callers hide the price then.
 */
export function formatPrice(price: number | string | null | undefined): string {
  if (price == null || price === '') return '';
  const n = Number(price);
  if (!Number.isFinite(n)) return String(price);
  if (n === 0) return 'Бесплатно';
  return `${n.toLocaleString('ru-RU', { maximumFractionDigits: 2 })} ₽`;
}

/** Russian plural form: plural(5, ['участник', 'участника', 'участников']) → 'участников'. */
export function plural(n: number, forms: [string, string, string]): string {
  const abs = Math.abs(n) % 100;
  const last = abs % 10;
  if (abs > 10 && abs < 20) return forms[2];
  if (last > 1 && last < 5) return forms[1];
  if (last === 1) return forms[0];
  return forms[2];
}

/**
 * Normalises a Telegram handle or link ("@name", "t.me/name", "https://t.me/name")
 * to an https URL, or returns null if it doesn't look like one.
 */
export function telegramUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  const v = value.trim();
  const m = v.match(/^(?:https?:\/\/)?(?:t\.me|telegram\.me)\/([A-Za-z0-9_+/-]+)$/i) ?? v.match(/^@?([A-Za-z][A-Za-z0-9_]{3,31})$/);
  return m ? `https://t.me/${m[1]}` : null;
}
