import { Capacitor } from '@capacitor/core';
import { getInitDataRaw } from './maxBridge';
import { getTelegramInitData } from './telegramBridge';

/**
 * Where the app is running:
 *  - 'max'      — mini app inside the MAX messenger (auth via signed init data)
 *  - 'telegram' — mini app inside Telegram (auth via signed init data)
 *  - 'native'   — the Android app built with Capacitor (email + password, JWT)
 *  - 'web'      — a plain browser outside any messenger (email + password, JWT)
 */
export type AppPlatform = 'max' | 'telegram' | 'native' | 'web';

export function getPlatform(): AppPlatform {
  if (getInitDataRaw()) return 'max';
  if (getTelegramInitData()) return 'telegram';
  if (Capacitor.isNativePlatform()) return 'native';
  return 'web';
}

export const isMaxApp = (): boolean => getPlatform() === 'max';
export const isTelegramApp = (): boolean => getPlatform() === 'telegram';
/** Inside a messenger mini app (MAX or Telegram): signed init data instead of a password. */
export const isMessengerApp = (): boolean => isMaxApp() || isTelegramApp();

/** Human name of the messenger the mini app runs in ("MAX" / "Telegram"), or null. */
export function messengerName(): string | null {
  const p = getPlatform();
  return p === 'max' ? 'MAX' : p === 'telegram' ? 'Telegram' : null;
}
export const isNativeApp = (): boolean => Capacitor.isNativePlatform();

// ── Backend location ─────────────────────────────────────────────────────────

/** axios base URL: "/api" when served next to the backend, absolute in the APK. */
export const API_BASE: string = import.meta.env.VITE_API_URL || 'http://localhost:8080';

/** Origin of the backend ("https://host"), or '' when the API is same-origin. */
export const API_ORIGIN: string = /^https?:\/\//i.test(API_BASE) ? new URL(API_BASE).origin : '';

/** STOMP endpoint on the backend. */
export function getWebSocketUrl(): string {
  const origin = API_ORIGIN || window.location.origin;
  return `${origin.replace(/^http/i, 'ws')}/ws`;
}

/**
 * Makes backend-relative media paths ("/uploads/photos/x.jpg") absolute when the
 * UI isn't served from the backend's origin (the APK). External URLs pass through.
 */
export function mediaUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  if (url.startsWith('/') && !url.startsWith('//') && API_ORIGIN) return API_ORIGIN + url;
  return url;
}

// ── JWT session (outside MAX) ────────────────────────────────────────────────

const TOKEN_KEY = 'join.authToken';

export function getAuthToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setAuthToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage unavailable — session lasts until reload
  }
}

/** Headers that authenticate the current user for REST and STOMP. */
export function getAuthHeaders(): Record<string, string> {
  const initData = getInitDataRaw();
  if (initData) return { 'X-Max-Init-Data': initData };
  const tgInitData = getTelegramInitData();
  if (tgInitData) return { 'X-Telegram-Init-Data': tgInitData };
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
