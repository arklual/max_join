import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { Box, Typography, Button } from '@mui/material';
import apiClient from '../api/client';
import Logo from '../components/Logo';
import { getAuthToken, isMessengerApp } from '../api/platform';
import { getStartParam } from '../api/maxBridge';
import { INVITE_START_PARAM_PREFIX, rememberPendingInvite, takePendingInvite } from '../utils/inviteLinks';

type SplashState = 'loading' | 'error';

export default function SplashScreen() {
  const navigate = useNavigate();
  const [state, setState] = useState<SplashState>('loading');
  const [errorMessage, setErrorMessage] = useState('');

  const checkUser = useCallback(async () => {
    setState('loading');
    setErrorMessage('');

    const splashMinDelay = new Promise<void>((resolve) => setTimeout(resolve, 1000));

    // Pull a friend-group invite code out of MAX start_param / URL hash
    // / URL search; if found, route to /friend-groups?invite=<code>
    // after auth so the join dialog auto-fires.
    const inviteCode = readFriendGroupInvite();
    if (inviteCode) rememberPendingInvite(inviteCode);
    const postAuthTarget = inviteCode ? `/friend-groups?invite=${inviteCode}` : '/afisha';

    // Outside MAX there is no signed init data: sign in with email + password first.
    if (!isMessengerApp() && !getAuthToken()) {
      await splashMinDelay;
      navigate('/login', { replace: true });
      return;
    }

    try {
      const [response] = await Promise.all([
        apiClient.get('/users/me'),
        splashMinDelay,
      ]);

      if (response.data && response.data.registered !== false) {
        takePendingInvite();
        navigate(postAuthTarget, { replace: true });
      } else {
        navigate('/register', { replace: true });
      }
    } catch (error) {
      await splashMinDelay;

      if (isAxiosError(error) && error.response) {
        const status = error.response.status;
        if (status === 403 || status === 404) {
          // In MAX: known user id but no JOIN profile yet. Outside MAX: the token is stale.
          navigate(isMessengerApp() ? '/register' : '/login', { replace: true });
          return;
        }
      }

      setErrorMessage('Не удалось подключиться к серверу');
      setState('error');
    }
  }, [navigate]);

  useEffect(() => {
    checkUser();
  }, [checkUser]);

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100dvh',
        // Brand gradient (same as the app icon) — fixed so white text stays readable in dark mode too.
        background: 'linear-gradient(135deg, #6750A4 0%, #A2457A 100%)',
      }}
    >
      <Box sx={{ mb: 1, animation: 'splashLogo 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards' }}>
        <Logo size={112} bare />
      </Box>
      <Typography
        variant="h2"
        sx={{
          fontWeight: 'bold',
          color: '#fff',
          mb: 4,
          animation: 'splashLogo 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards',
        }}
      >
        JOIN
      </Typography>

      {state === 'error' && (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, animation: 'fadeInUp 0.4s ease-out' }}>
          <Typography sx={{ color: '#fff' }}>
            {errorMessage}
          </Typography>
          <Button
            variant="outlined"
            onClick={checkUser}
            sx={{
              borderColor: '#fff',
              color: '#fff',
              '&:hover': {
                borderColor: '#fff',
                backgroundColor: 'rgba(255,255,255,0.1)',
              },
            }}
          >
            Повторить
          </Button>
        </Box>
      )}
    </Box>
  );
}

function readFriendGroupInvite(): string | null {
  // Candidates in priority order:
  //  1. MAX Bridge  — window.WebApp.initDataUnsafe.start_param
  //  2. URL hash    — #WebAppStartParam=join_<code> (raw launch params)
  //  3. URL search  — ?startapp=...   (dev / browser preview)
  //                   ?invite=<code>  (open_app button URL we send
  //                                    from bot /start join_<code>)
  const candidates: (string | null | undefined)[] = [getStartParam()];

  try {
    const rawHash = window.location.hash.startsWith('#')
      ? window.location.hash.slice(1)
      : window.location.hash;
    if (rawHash) {
      const hashParams = new URLSearchParams(rawHash);
      candidates.push(hashParams.get('WebAppStartParam'));
    }
  } catch {
    // ignore
  }

  let searchInvite: string | null = null;
  try {
    const params = new URLSearchParams(window.location.search);
    candidates.push(params.get('startapp'));
    searchInvite = params.get('invite');
  } catch {
    // ignore
  }

  for (const raw of candidates) {
    if (!raw) continue;
    if (!raw.startsWith(INVITE_START_PARAM_PREFIX)) continue;
    const code = raw.slice(INVITE_START_PARAM_PREFIX.length).trim();
    if (code && /^[A-Za-z0-9_-]{1,64}$/.test(code)) {
      return code.toLowerCase();
    }
  }
  if (searchInvite && /^[A-Za-z0-9_-]{1,64}$/.test(searchInvite.trim())) {
    return searchInvite.trim().toLowerCase();
  }
  return null;
}

function isAxiosError(error: unknown): error is { response: { status: number } } {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as { response?: unknown }).response === 'object' &&
    (error as { response: { status?: unknown } }).response !== null &&
    'status' in ((error as { response: object }).response)
  );
}
