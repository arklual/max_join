import { createContext, type FC, type ReactNode } from 'react';
import type { ThemeMode } from '../types/ThemeMode';
import { useThemeMode, type ThemePreference } from '../hooks/useThemeMode';

export type ThemeModeContextType = {
    /** Effective mode used for rendering. */
    themeMode: ThemeMode,
    /** User choice: 'system' | 'light' | 'dark'. */
    preference: ThemePreference,
    setPreference: (value: ThemePreference) => void,
    toggleTheme: () => void,
    setThemeMode: (mode: ThemeMode) => void
};

export interface ThemeModeProviderProps {
    children?: ReactNode;
}

export const ThemeModeContext = createContext<ThemeModeContextType>({
    themeMode: 'light',
    preference: 'system',
    setPreference: () => { },
    toggleTheme: () => { },
    setThemeMode: () => { }
});

const ThemeModeProvider: FC<ThemeModeProviderProps> = ({ children }) => {
    const { themeMode, preference, setPreference, toggleTheme } = useThemeMode();

    return (
        <ThemeModeContext.Provider value={{ themeMode, preference, setPreference, toggleTheme, setThemeMode: setPreference }}>
            {children}
        </ThemeModeContext.Provider>
    );
};

export default ThemeModeProvider;
