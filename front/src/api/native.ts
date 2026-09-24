/**
 * Native (Capacitor / Android) integrations. Every helper is a no-op or falls
 * back to the web API outside the native app, so shared screens can call them
 * unconditionally. Plugins are imported lazily to keep the web bundle small.
 */
import { registerPlugin } from '@capacitor/core';
import { isNativeApp } from './platform';

export async function nativeOpenUrl(url: string): Promise<boolean> {
  if (!isNativeApp()) return false;
  const { Browser } = await import('@capacitor/browser');
  await Browser.open({ url });
  return true;
}

/** Native share sheet. Returns false when unavailable (caller falls back to copying). */
export async function nativeShare(params: { title?: string; text?: string; url?: string }): Promise<boolean> {
  if (!isNativeApp()) return false;
  try {
    const { Share } = await import('@capacitor/share');
    await Share.share({ ...params, dialogTitle: params.title });
    return true;
  } catch {
    return false;
  }
}

export async function nativeCopy(text: string): Promise<boolean> {
  if (!isNativeApp()) return false;
  try {
    const { Clipboard } = await import('@capacitor/clipboard');
    await Clipboard.write({ string: text });
    return true;
  } catch {
    return false;
  }
}

/**
 * Scans a QR code with Google's code scanner (no camera permission prompt).
 * Resolves with the raw text, or null if cancelled / unavailable.
 */
export async function nativeScanQr(): Promise<string | null> {
  if (!isNativeApp()) return null;
  const { BarcodeScanner, BarcodeFormat } = await import('@capacitor-mlkit/barcode-scanning');
  try {
    const { available } = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
    if (!available) {
      await BarcodeScanner.installGoogleBarcodeScannerModule();
    }
    const { barcodes } = await BarcodeScanner.scan({ formats: [BarcodeFormat.QrCode] });
    return barcodes[0]?.rawValue ?? null;
  } catch {
    return null;
  }
}

/**
 * Android hardware back: go back in the in-app history, or leave the app from a
 * root screen. `navigateBack` receives control when there is history to pop.
 */
export async function registerBackButton(navigateBack: () => void): Promise<void> {
  if (!isNativeApp()) return;
  const { App } = await import('@capacitor/app');
  await App.addListener('backButton', () => {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0;
    if (idx > 0) {
      navigateBack();
    } else {
      App.exitApp();
    }
  });
}

/** Matches the Android status bar to the current theme surface. */
export async function syncStatusBar(dark: boolean, color: string): Promise<void> {
  if (!isNativeApp()) return;
  try {
    const { StatusBar, Style } = await import('@capacitor/status-bar');
    // Colour first, then icon style: Style.Light = dark icons for a light bar.
    // (No setOverlaysWebView — its legacy systemUiVisibility write can reset the icon style.)
    await StatusBar.setBackgroundColor({ color });
    await StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light });
  } catch {
    // older WebView / plugin missing — cosmetic only
  }
}

export async function hideSplash(): Promise<void> {
  if (!isNativeApp()) return;
  try {
    const { SplashScreen } = await import('@capacitor/splash-screen');
    await SplashScreen.hide();
  } catch {
    // ignore
  }
}

interface SystemThemePlugin {
  get(): Promise<{ dark: boolean }>;
  addListener(event: 'change', cb: (s: { dark: boolean }) => void): Promise<{ remove: () => Promise<void> }>;
}

// Registered synchronously: a Capacitor plugin proxy answers *any* property,
// including `then`, so it must never be returned from an async function or awaited.
const SystemTheme = registerPlugin<SystemThemePlugin>('SystemTheme');

/** System night mode as reported by Android (null outside the native app). */
export async function getNativeDarkMode(): Promise<boolean | null> {
  if (!isNativeApp()) return null;
  try {
    const { dark } = await SystemTheme.get();
    return dark;
  } catch {
    return null;
  }
}

/** Subscribes to Android night-mode changes; returns an unsubscribe function. */
export function onNativeDarkModeChange(cb: (dark: boolean) => void): () => void {
  if (!isNativeApp()) return () => {};
  let remove: (() => Promise<void>) | null = null;
  let cancelled = false;
  void SystemTheme.addListener('change', (s) => cb(s.dark))
    .then((handle) => {
      if (cancelled) void handle.remove();
      else remove = handle.remove;
    })
    .catch(() => {});
  return () => {
    cancelled = true;
    void remove?.();
  };
}
