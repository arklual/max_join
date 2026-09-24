import Box from '@mui/material/Box';

interface LogoProps {
  /** Edge length in px. */
  size?: number;
  /** Draw on a transparent background (white rings only), e.g. over a gradient. */
  bare?: boolean;
}

/** JOIN mark: two linked rings — people joining up. Same artwork as the app icon. */
export default function Logo({ size = 64, bare = false }: LogoProps) {
  return (
    <Box
      component="svg"
      viewBox="0 0 512 512"
      role="img"
      aria-label="JOIN"
      sx={{ width: size, height: size, display: 'block', flexShrink: 0 }}
    >
      <defs>
        <linearGradient id="join-logo-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#6750A4" />
          <stop offset="1" stopColor="#A2457A" />
        </linearGradient>
      </defs>
      {!bare && <rect width="512" height="512" rx="120" fill="url(#join-logo-g)" />}
      <circle cx="206" cy="256" r="98" fill="none" stroke="#fff" strokeWidth="44" />
      <circle cx="306" cy="256" r="98" fill="none" stroke="#fff" strokeWidth="44" strokeOpacity="0.85" />
    </Box>
  );
}
