/**
 * Thin typed wrapper around the Telegram Mini App API (`window.Telegram.WebApp`),
 * loaded from https://telegram.org/js/telegram-web-app.js in index.html.
 * Outside Telegram `initData` is empty and helpers degrade gracefully.
 */

export interface TelegramWebApp {
  initData?: string;
  initDataUnsafe?: { start_param?: string; user?: { id: number } };
  colorScheme?: 'light' | 'dark';
  version?: string;
  ready?: () => void;
  expand?: () => void;
  isVersionAtLeast?: (v: string) => boolean;
  openLink?: (url: string) => void;
  openTelegramLink?: (url: string) => void;
  showScanQrPopup?: (params: { text?: string }, callback: (text: string) => boolean | void) => void;
  closeScanQrPopup?: () => void;
  onEvent?: (event: string, cb: () => void) => void;
  offEvent?: (event: string, cb: () => void) => void;
  setHeaderColor?: (color: string) => void;
  setBackgroundColor?: (color: string) => void;
  setBottomBarColor?: (color: string) => void;
}

export function getTelegramWebApp(): TelegramWebApp | null {
  try {
    return (window as unknown as { Telegram?: { WebApp?: TelegramWebApp } }).Telegram?.WebApp ?? null;
  } catch {
    return null;
  }
}

/** Signed init data for the backend, or null when not running inside Telegram. */
export function getTelegramInitData(): string | null {
  const data = getTelegramWebApp()?.initData;
  return data && data.length > 0 ? data : null;
}

export function getTelegramStartParam(): string | null {
  return getTelegramWebApp()?.initDataUnsafe?.start_param ?? null;
}

/** Telegram's own colour scheme (null outside Telegram). */
export function getTelegramColorScheme(): 'light' | 'dark' | null {
  return getTelegramInitData() ? getTelegramWebApp()?.colorScheme ?? null : null;
}

export function onTelegramThemeChange(cb: (dark: boolean) => void): () => void {
  const app = getTelegramWebApp();
  if (!getTelegramInitData() || !app?.onEvent) return () => {};
  const handler = () => cb(app.colorScheme === 'dark');
  app.onEvent('themeChanged', handler);
  return () => app.offEvent?.('themeChanged', handler);
}

function supports(version: string): boolean {
  const app = getTelegramWebApp();
  return !!app?.isVersionAtLeast?.(version);
}

/** Paints Telegram's header / background / bottom bar in the app's surface colour. */
export function syncTelegramChrome(color: string): void {
  const app = getTelegramWebApp();
  if (!getTelegramInitData() || !app) return;
  try {
    if (supports('6.1')) {
      app.setHeaderColor?.(color);
      app.setBackgroundColor?.(color);
    }
    if (supports('7.10')) app.setBottomBarColor?.(color);
  } catch {
    // cosmetic only
  }
}

export function isTelegramQrSupported(): boolean {
  return !!getTelegramInitData() && supports('6.4') && typeof getTelegramWebApp()?.showScanQrPopup === 'function';
}

/** Telegram's QR scanner: resolves with the first scanned text, or null if closed. */
export function telegramScanQr(prompt: string): Promise<string | null> {
  const app = getTelegramWebApp();
  return new Promise((resolve) => {
    if (!app?.showScanQrPopup) {
      resolve(null);
      return;
    }
    let done = false;
    const onClosed = () => {
      app.offEvent?.('scanQrPopupClosed', onClosed);
      if (!done) resolve(null);
    };
    app.onEvent?.('scanQrPopupClosed', onClosed);
    app.showScanQrPopup({ text: prompt }, (text) => {
      done = true;
      app.offEvent?.('scanQrPopupClosed', onClosed);
      resolve(text);
      return true;
    });
  });
}
