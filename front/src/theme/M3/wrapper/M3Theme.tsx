// @ts-nocheck

import { useContext, useEffect, useMemo } from "react";
import { CssBaseline, createTheme, ThemeProvider } from '@mui/material';
import { deepmerge } from "@mui/utils";
import { ThemeModeContext } from "../providers/ThemeModeProvider";
import { ThemeSchemeContext } from "../providers/ThemeSchemeProvider";
import { getMUIPalette } from "../utils/getMUIPalette";
import { getMUIComponents } from "../utils/getMUIComponents";
import { syncStatusBar } from "../../../api/native";
import { syncTelegramChrome } from "../../../api/telegramBridge";


interface M3Props {
    children?: React.ReactNode;
};

const M3Theme = ({ children }: M3Props) => {

    const { themeMode } = useContext(ThemeModeContext);
    const { themeScheme } = useContext(ThemeSchemeContext);

    const m3Theme = useMemo(() => {

        const muiPalette = getMUIPalette(themeMode, themeScheme);

        let theme = createTheme(muiPalette);
        theme = deepmerge(theme, getMUIComponents(theme));

        return theme;

    }, [themeMode, themeScheme]);

    // Keep browser chrome / Android status bar and the page background in step with the theme.
    useEffect(() => {
        const surface = m3Theme.palette.surface?.main ?? m3Theme.palette.background.default;
        document.documentElement.style.colorScheme = themeMode;
        document.documentElement.style.backgroundColor = surface;
        let meta = document.querySelector('meta[name="theme-color"]');
        if (!meta) {
            meta = document.createElement('meta');
            meta.setAttribute('name', 'theme-color');
            document.head.appendChild(meta);
        }
        meta.setAttribute('content', surface);
        void syncStatusBar(themeMode === 'dark', surface);
        syncTelegramChrome(surface);
    }, [m3Theme, themeMode]);

    return (
        <ThemeProvider theme={m3Theme}>
            <CssBaseline enableColorScheme />
            {children}
        </ThemeProvider>
    );
}

export default M3Theme;