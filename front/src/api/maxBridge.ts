import { Capacitor } from '@capacitor/core';
import {
  getTelegramInitData,
  getTelegramStartParam,
  getTelegramWebApp,
  isTelegramQrSupported,
  telegramScanQr,
} from './telegramBridge';

/**
 * Thin typed wrapper around the MAX Bridge (`window.WebApp`), loaded from
 * https://st.max.ru/js/max-web-app.js in index.html.
 * Outside MAX (plain browser, dev preview) every helper degrades gracefully.
 */

export interface MaxWebApp {
  initData?: string;
  initDataUnsafe?: {
    start_param?: string;
    user?: { id: number; first_name?: string; last_name?: string; username?: string };
  };
  platform?: string;
  ready?: () => void;
  openLink?: (url: string) => void;
  openMaxLink?: (url: string) => void;
  openCodeReader?: (fileSelect?: boolean) => Promise<string | { value?: string }>;
  shareMaxContent?: (params: { text?: string; link?: string }) => Promise<unknown>;
  shareContent?: (params: { text?: string; link?: string }) => Promise<unknown>;
  BackButton?: {
    show: () => void;
    hide: () => void;
    onClick: (cb: () => void) => void;
    offClick: (cb: () => void) => void;
  };
  HapticFeedback?: {
    impactOccurred: (style: 'soft' | 'light' | 'medium' | 'heavy' | 'rigid', disableVibrationFallback?: boolean) => void;
    notificationOccurred: (type: 'error' | 'success' | 'warning', disableVibrationFallback?: boolean) => void;
  };
}

export function getMaxWebApp(): MaxWebApp | null {
  try {
    const w = window as unknown as { WebApp?: MaxWebApp };
    return w.WebApp ?? null;
  } catch {
    return null;
  }
}

/** Raw signed init data to be validated on the backend, or null outside MAX. */
export function getInitDataRaw(): string | null {
  const data = getMaxWebApp()?.initData;
  return data && data.length > 0 ? data : null;
}

export function getStartParam(): string | null {
  return getMaxWebApp()?.initDataUnsafe?.start_param ?? getTelegramStartParam();
}

export function isCodeReaderSupported(): boolean {
  if (Capacitor.isNativePlatform()) return true;
  if (isTelegramQrSupported()) return true;
  const app = getMaxWebApp();
  return !!getInitDataRaw() && typeof app?.openCodeReader === 'function';
}

/** Opens the native QR scanner; resolves with decoded text or null if cancelled. */
export async function scanQrCode(): Promise<string | null> {
  if (Capacitor.isNativePlatform()) {
    const { nativeScanQr } = await import('./native');
    return nativeScanQr();
  }
  if (isTelegramQrSupported()) {
    return telegramScanQr('Наведите камеру на QR-код приглашения JOIN');
  }
  const app = getMaxWebApp();
  if (!app?.openCodeReader) return null;
  try {
    const result = await app.openCodeReader(true);
    if (typeof result === 'string') return result;
    return result?.value ?? null;
  } catch {
    return null;
  }
}

/**
 * Opens a link from inside the mini app. max.ru deep links stay inside MAX;
 * everything else goes to the external browser via the bridge. Outside MAX
 * falls back to a new tab.
 */
export function openExternalLink(url: string): void {
  if (Capacitor.isNativePlatform()) {
    void import('./native').then(({ nativeOpenUrl }) => nativeOpenUrl(url));
    return;
  }
  const tg = getTelegramWebApp();
  if (getTelegramInitData() && tg) {
    try {
      const host = new URL(url).hostname;
      if (/(^|\.)(t\.me|telegram\.me)$/i.test(host) && tg.openTelegramLink) tg.openTelegramLink(url);
      else if (tg.openLink) tg.openLink(url);
      else window.open(url, '_blank', 'noopener,noreferrer');
      return;
    } catch {
      // invalid URL — fall through
    }
  }
  const app = getMaxWebApp();
  try {
    const host = new URL(url).hostname;
    if (/(^|\.)max\.ru$/i.test(host) && app?.openMaxLink) {
      app.openMaxLink(url);
      return;
    }
    if (app?.openLink && getInitDataRaw()) {
      app.openLink(url);
      return;
    }
  } catch {
    // invalid URL — fall through to window.open
  }
  window.open(url, '_blank', 'noopener,noreferrer');
}

/**
 * Opens a max.ru deep link so that the MAX app handles it: in the Android app the
 * system resolves the link to the installed MAX (Custom Tabs wouldn't); elsewhere
 * it goes through openExternalLink.
 */
export function openMaxDeepLink(url: string): void {
  if (Capacitor.isNativePlatform()) {
    window.location.href = url;
    return;
  }
  openExternalLink(url);
}

/** Tactile feedback on key actions (MAX mobile only; a no-op elsewhere). */
export function haptic(kind: 'light' | 'success' | 'error'): void {
  const feedback = getInitDataRaw() ? getMaxWebApp()?.HapticFeedback : undefined;
  try {
    if (kind === 'light') feedback?.impactOccurred('light', true);
    else feedback?.notificationOccurred(kind, true);
  } catch {
    // unsupported on this platform
  }
}

/**
 * Shares a link to a MAX chat of the user's choice. Returns false when sharing inside MAX
 * is unavailable, so the caller can fall back (system share sheet / copy).
 */
export async function shareToMax(text: string, link: string): Promise<boolean> {
  const app = getInitDataRaw() ? getMaxWebApp() : null;
  if (!app?.shareMaxContent) return false;
  try {
    await app.shareMaxContent({ text, link });
    return true;
  } catch {
    return false;
  }
}

/** MAX system back button; returns a cleanup function. No-op outside MAX. */
export function showMaxBackButton(onBack: () => void): () => void {
  const button = getInitDataRaw() ? getMaxWebApp()?.BackButton : undefined;
  if (!button) return () => {};
  try {
    button.onClick(onBack);
    button.show();
  } catch {
    return () => {};
  }
  return () => {
    try {
      button.offClick(onBack);
      button.hide();
    } catch {
      // ignore
    }
  };
}
