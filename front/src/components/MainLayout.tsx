import { Outlet, useLocation, useNavigate } from 'react-router';
import { useMemo } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Typography from '@mui/material/Typography';
import TheaterComedy from '@mui/icons-material/TheaterComedy';
import FavoriteBorder from '@mui/icons-material/FavoriteBorder';
import ChatBubbleOutline from '@mui/icons-material/ChatBubbleOutline';
import Groups from '@mui/icons-material/Groups';
import PersonOutline from '@mui/icons-material/PersonOutline';
import type { ReactElement } from 'react';
import NotificationsBell from './NotificationsBell';
import ConsentGate from './ConsentGate';

interface TabItem {
  path: string;
  label: string;
  icon: ReactElement;
}

const TABS: TabItem[] = [
  { path: '/afisha', label: 'Афиша', icon: <TheaterComedy /> },
  { path: '/likes', label: 'Избранное', icon: <FavoriteBorder /> },
  { path: '/chats', label: 'Чаты', icon: <ChatBubbleOutline /> },
  { path: '/groups', label: 'Группы', icon: <Groups /> },
  { path: '/profile', label: 'Профиль', icon: <PersonOutline /> },
];

export default function MainLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  // Someone else's profile (/profile/:id) isn't the "Профиль" tab.
  const isOtherProfile = /^\/profile\/\d+/.test(location.pathname);
  const currentTab = isOtherProfile
    ? -1
    : TABS.findIndex((tab) => location.pathname.startsWith(tab.path));

  // Use top-level path segment as animation key so sub-routes don't re-trigger
  const pageKey = useMemo(() => {
    const seg = location.pathname.split('/').filter(Boolean)[0] ?? '';
    return seg;
  }, [location.pathname]);

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100dvh',
        bgcolor: 'surface.main',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 1.5,
          py: 0.75,
          borderBottom: 1,
          borderColor: 'outlineVariant.main',
          bgcolor: 'surface.main',
        }}
      >
        <Typography
          variant="subtitle1"
          sx={{ fontWeight: 800, letterSpacing: 1, color: 'primary.main' }}
        >
          JOIN
        </Typography>
        <NotificationsBell />
      </Box>

      <Box
        key={pageKey}
        sx={{
          flex: 1,
          overflow: 'auto',
          animation: 'fadeIn 0.25s ease-out',
        }}
      >
        <Outlet />
      </Box>

      <Paper
        elevation={0}
        sx={{
          bgcolor: 'surfaceContainer.main',
          borderTop: 1,
          borderColor: 'outlineVariant.main',
        }}
      >
        <BottomNavigation
          value={currentTab}
          onChange={(_event, newValue) => {
            navigate(TABS[newValue].path);
          }}
          showLabels
          sx={{
            bgcolor: 'transparent',
            '& .MuiBottomNavigationAction-root': {
              color: 'onSurfaceVariant.main',
              minWidth: 0,
              '&.Mui-selected': {
                color: 'primary.main',
              },
            },
          }}
        >
          {TABS.map((tab) => (
            <BottomNavigationAction
              key={tab.path}
              label={tab.label}
              icon={tab.icon}
            />
          ))}
        </BottomNavigation>
      </Paper>
      <ConsentGate />
    </Box>
  );
}
