import { useCallback, useEffect, useState } from 'react';
import type { ThemeMode } from '../types/ThemeMode';
import { onNativeDarkModeChange } from '../../../api/native';
import { getTelegramColorScheme, onTelegramThemeChange } from '../../../api/telegramBridge';

export const THEME_MODE_KEY = 'ThemeModeKey';

/** What the user picked; 'system' follows the OS / messenger colour scheme. */
export type ThemePreference = 'system' | ThemeMode;

const DARK_QUERY = '(prefers-color-scheme: dark)';

function readPreference(): ThemePreference {
    try {
        const raw = localStorage.getItem(THEME_MODE_KEY);
        const value = raw ? JSON.parse(raw) : null;
        return value === 'light' || value === 'dark' ? value : 'system';
    } catch {
        return 'system';
    }
}

function systemMode(): ThemeMode {
    // In the Android app the native night mode is read before first render (main.tsx):
    // the WebView's own prefers-color-scheme doesn't reliably follow the system.
    const native = (window as { __JOIN_NATIVE_DARK__?: boolean }).__JOIN_NATIVE_DARK__;
    if (typeof native === 'boolean') return native ? 'dark' : 'light';
    // Inside Telegram follow Telegram's own theme rather than the OS one.
    const telegram = getTelegramColorScheme();
    if (telegram) return telegram;
    return typeof window !== 'undefined' && window.matchMedia?.(DARK_QUERY).matches ? 'dark' : 'light';
}

export const useThemeMode = () => {
    const [preference, setPreferenceState] = useState<ThemePreference>(readPreference);
    const [system, setSystem] = useState<ThemeMode>(systemMode);

    useEffect(() => {
        const offTelegram = onTelegramThemeChange((dark) => setSystem(dark ? 'dark' : 'light'));
        const offNative = onNativeDarkModeChange((dark) => {
            (window as { __JOIN_NATIVE_DARK__?: boolean }).__JOIN_NATIVE_DARK__ = dark;
            setSystem(dark ? 'dark' : 'light');
        });
        const mql = window.matchMedia?.(DARK_QUERY);
        const isHostDriven = typeof (window as { __JOIN_NATIVE_DARK__?: boolean }).__JOIN_NATIVE_DARK__ === 'boolean'
            || getTelegramColorScheme() !== null;
        if (!mql || isHostDriven) return () => {
            offNative();
            offTelegram();
        };
        const onChange = () => setSystem(mql.matches ? 'dark' : 'light');
        mql.addEventListener?.('change', onChange);
        return () => {
            offNative();
            offTelegram();
            mql.removeEventListener?.('change', onChange);
        };
    }, []);

    const setPreference = useCallback((value: ThemePreference) => {
        setPreferenceState(value);
        try {
            if (value === 'system') localStorage.removeItem(THEME_MODE_KEY);
            else localStorage.setItem(THEME_MODE_KEY, JSON.stringify(value));
        } catch {
            // storage unavailable — preference lasts for this session
        }
    }, []);

    const themeMode: ThemeMode = preference === 'system' ? system : preference;

    const toggleTheme = useCallback(() => {
        setPreference(themeMode === 'light' ? 'dark' : 'light');
    }, [themeMode, setPreference]);

    return { themeMode, preference, setPreference, toggleTheme };
};
