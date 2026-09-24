import '@mui/material/styles';

declare module '@mui/material/styles' {
  interface Palette {
    onPrimary: import('@mui/material/styles').PaletteColor;
    primaryContainer: import('@mui/material/styles').PaletteColor;
    onPrimaryContainer: import('@mui/material/styles').PaletteColor;

    onSecondary: import('@mui/material/styles').PaletteColor;
    secondaryContainer: import('@mui/material/styles').PaletteColor;
    onSecondaryContainer: import('@mui/material/styles').PaletteColor;

    tertiary: import('@mui/material/styles').PaletteColor;
    onTertiary: import('@mui/material/styles').PaletteColor;
    tertiaryContainer: import('@mui/material/styles').PaletteColor;
    onTertiaryContainer: import('@mui/material/styles').PaletteColor;

    onError: import('@mui/material/styles').PaletteColor;
    errorContainer: import('@mui/material/styles').PaletteColor;
    onErrorContainer: import('@mui/material/styles').PaletteColor;

    primaryFixed: import('@mui/material/styles').PaletteColor;
    primaryFixedDim: import('@mui/material/styles').PaletteColor;
    onPrimaryFixed: import('@mui/material/styles').PaletteColor;
    onPrimaryFixedVariant: import('@mui/material/styles').PaletteColor;

    secondaryFixed: import('@mui/material/styles').PaletteColor;
    secondaryFixedDim: import('@mui/material/styles').PaletteColor;
    onSecondaryFixed: import('@mui/material/styles').PaletteColor;
    onSecondaryFixedVariant: import('@mui/material/styles').PaletteColor;

    tertiaryFixed: import('@mui/material/styles').PaletteColor;
    tertiaryFixedDim: import('@mui/material/styles').PaletteColor;
    onTertiaryFixed: import('@mui/material/styles').PaletteColor;
    onTertiaryFixedVariant: import('@mui/material/styles').PaletteColor;

    surface: import('@mui/material/styles').PaletteColor;
    onSurface: import('@mui/material/styles').PaletteColor;

    surfaceDim: import('@mui/material/styles').PaletteColor;
    surfaceBright: import('@mui/material/styles').PaletteColor;

    surfaceContainerLowest: import('@mui/material/styles').PaletteColor;
    surfaceContainerLow: import('@mui/material/styles').PaletteColor;
    surfaceContainer: import('@mui/material/styles').PaletteColor;
    surfaceContainerHigh: import('@mui/material/styles').PaletteColor;
    surfaceContainerHighest: import('@mui/material/styles').PaletteColor;

    surfaceVariant: import('@mui/material/styles').PaletteColor;
    onSurfaceVariant: import('@mui/material/styles').PaletteColor;

    outline: import('@mui/material/styles').PaletteColor;
    outlineVariant: import('@mui/material/styles').PaletteColor;

    inversePrimary: import('@mui/material/styles').PaletteColor;
    inverseOnPrimary: import('@mui/material/styles').PaletteColor;
    inverseSurface: import('@mui/material/styles').PaletteColor;
    inverseOnSurface: import('@mui/material/styles').PaletteColor;

    shadow: import('@mui/material/styles').PaletteColor;
    scrim: import('@mui/material/styles').PaletteColor;

    surfaceTintColor: import('@mui/material/styles').PaletteColor;

    onBackground: import('@mui/material/styles').PaletteColor;

    onInfo: import('@mui/material/styles').PaletteColor;
    infoContainer: import('@mui/material/styles').PaletteColor;
    onInfoContainer: import('@mui/material/styles').PaletteColor;

    onSuccess: import('@mui/material/styles').PaletteColor;
    successContainer: import('@mui/material/styles').PaletteColor;
    onSuccessContainer: import('@mui/material/styles').PaletteColor;

    onWarning: import('@mui/material/styles').PaletteColor;
    warningContainer: import('@mui/material/styles').PaletteColor;
    onWarningContainer: import('@mui/material/styles').PaletteColor;
  }

  interface PaletteOptions {
    themeMode?: string;
    onPrimary?: import('@mui/material/styles').PaletteColorOptions;
    primaryContainer?: import('@mui/material/styles').PaletteColorOptions;
    onPrimaryContainer?: import('@mui/material/styles').PaletteColorOptions;
    onSecondary?: import('@mui/material/styles').PaletteColorOptions;
    secondaryContainer?: import('@mui/material/styles').PaletteColorOptions;
    onSecondaryContainer?: import('@mui/material/styles').PaletteColorOptions;
    tertiary?: import('@mui/material/styles').PaletteColorOptions;
    onTertiary?: import('@mui/material/styles').PaletteColorOptions;
    tertiaryContainer?: import('@mui/material/styles').PaletteColorOptions;
    onTertiaryContainer?: import('@mui/material/styles').PaletteColorOptions;
    onError?: import('@mui/material/styles').PaletteColorOptions;
    errorContainer?: import('@mui/material/styles').PaletteColorOptions;
    onErrorContainer?: import('@mui/material/styles').PaletteColorOptions;
    primaryFixed?: import('@mui/material/styles').PaletteColorOptions;
    primaryFixedDim?: import('@mui/material/styles').PaletteColorOptions;
    onPrimaryFixed?: import('@mui/material/styles').PaletteColorOptions;
    onPrimaryFixedVariant?: import('@mui/material/styles').PaletteColorOptions;
    secondaryFixed?: import('@mui/material/styles').PaletteColorOptions;
    secondaryFixedDim?: import('@mui/material/styles').PaletteColorOptions;
    onSecondaryFixed?: import('@mui/material/styles').PaletteColorOptions;
    onSecondaryFixedVariant?: import('@mui/material/styles').PaletteColorOptions;
    tertiaryFixed?: import('@mui/material/styles').PaletteColorOptions;
    tertiaryFixedDim?: import('@mui/material/styles').PaletteColorOptions;
    onTertiaryFixed?: import('@mui/material/styles').PaletteColorOptions;
    onTertiaryFixedVariant?: import('@mui/material/styles').PaletteColorOptions;
    surface?: import('@mui/material/styles').PaletteColorOptions;
    onSurface?: import('@mui/material/styles').PaletteColorOptions;
    surfaceDim?: import('@mui/material/styles').PaletteColorOptions;
    surfaceBright?: import('@mui/material/styles').PaletteColorOptions;
    surfaceContainerLowest?: import('@mui/material/styles').PaletteColorOptions;
    surfaceContainerLow?: import('@mui/material/styles').PaletteColorOptions;
    surfaceContainer?: import('@mui/material/styles').PaletteColorOptions;
    surfaceContainerHigh?: import('@mui/material/styles').PaletteColorOptions;
    surfaceContainerHighest?: import('@mui/material/styles').PaletteColorOptions;
    surfaceVariant?: import('@mui/material/styles').PaletteColorOptions;
    onSurfaceVariant?: import('@mui/material/styles').PaletteColorOptions;
    outline?: import('@mui/material/styles').PaletteColorOptions;
    outlineVariant?: import('@mui/material/styles').PaletteColorOptions;
    inversePrimary?: import('@mui/material/styles').PaletteColorOptions;
    inverseOnPrimary?: import('@mui/material/styles').PaletteColorOptions;
    inverseSurface?: import('@mui/material/styles').PaletteColorOptions;
    inverseOnSurface?: import('@mui/material/styles').PaletteColorOptions;
    shadow?: import('@mui/material/styles').PaletteColorOptions;
    scrim?: import('@mui/material/styles').PaletteColorOptions;
    surfaceTintColor?: import('@mui/material/styles').PaletteColorOptions;
    onBackground?: import('@mui/material/styles').PaletteColorOptions;
    onInfo?: import('@mui/material/styles').PaletteColorOptions;
    infoContainer?: import('@mui/material/styles').PaletteColorOptions;
    onInfoContainer?: import('@mui/material/styles').PaletteColorOptions;
    onSuccess?: import('@mui/material/styles').PaletteColorOptions;
    successContainer?: import('@mui/material/styles').PaletteColorOptions;
    onSuccessContainer?: import('@mui/material/styles').PaletteColorOptions;
    onWarning?: import('@mui/material/styles').PaletteColorOptions;
    warningContainer?: import('@mui/material/styles').PaletteColorOptions;
    onWarningContainer?: import('@mui/material/styles').PaletteColorOptions;
  }

  interface ThemeOptions {
    tones?: unknown;
  }

  interface Theme {
    tones?: unknown;
  }
}

declare module '@mui/material/Button' {
  interface ButtonPropsVariantOverrides {
    elevated: true;
    filled: true;
    tonal: true;
  }
}
