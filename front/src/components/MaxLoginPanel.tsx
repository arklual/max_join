import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { Alert, Box, Button, CircularProgress, Collapse, Typography } from '@mui/material';
import QRCode from 'qrcode';
import apiClient from '../api/client';
import { openMaxDeepLink } from '../api/maxBridge';
import { setAuthToken } from '../api/platform';
import { takePendingInvite } from '../utils/inviteLinks';
import Logo from './Logo';

const POLL_MS = 2000;
const TTL_MS = 10 * 60 * 1000;

type Status = 'PENDING' | 'SUCCESS' | 'NEEDS_REGISTRATION' | 'EXPIRED';

/**
 * "Войти через MAX": opens the JOIN bot in MAX with a one-time login token and
 * waits for the user to press Start. Existing users are signed in; new MAX users
 * go on to a password-less registration bound to their MAX account.
 */
export default function MaxLoginPanel() {
  const navigate = useNavigate();
  const [login, setLogin] = useState<{ token: string; url: string } | null>(null);
  const [qr, setQr] = useState('');
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');
  const startedAt = useRef(0);
  const finished = useRef(false);

  const check = useCallback(async () => {
    if (!login || finished.current) return;
    if (Date.now() - startedAt.current > TTL_MS) {
      setLogin(null);
      setError('Время на вход истекло — попробуйте ещё раз.');
      return;
    }
    try {
      const res = await apiClient.get<{ status: Status; token?: string }>(`/auth/max-login/${login.token}`);
      if (res.data.status === 'SUCCESS' && res.data.token) {
        finished.current = true;
        setAuthToken(res.data.token);
        const invite = takePendingInvite();
        navigate(invite ? `/friend-groups?invite=${invite}` : '/afisha', { replace: true });
      } else if (res.data.status === 'NEEDS_REGISTRATION') {
        finished.current = true;
        navigate('/register', { state: { maxLoginToken: login.token } });
      } else if (res.data.status === 'EXPIRED') {
        setLogin(null);
        setError('Ссылка для входа устарела — попробуйте ещё раз.');
      }
    } catch {
      // network hiccup — try again on the next tick
    }
  }, [login, navigate]);

  useEffect(() => {
    if (!login) return;
    const timer = window.setInterval(() => void check(), POLL_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible') void check();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [login, check]);

  useEffect(() => {
    if (!login) {
      setQr('');
      return;
    }
    QRCode.toDataURL(login.url, { margin: 1, width: 360 }).then(setQr).catch(() => setQr(''));
  }, [login]);

  async function handleStart() {
    setError('');
    setStarting(true);
    try {
      const res = await apiClient.post<{ token: string; url: string }>('/auth/max-login');
      startedAt.current = Date.now();
      finished.current = false;
      setLogin(res.data);
      openMaxDeepLink(res.data.url);
    } catch {
      setError('Не удалось связаться с сервером. Проверьте интернет.');
    } finally {
      setStarting(false);
    }
  }

  return (
    <Box>
      {error && <Alert severity="warning" sx={{ mb: 2 }}>{error}</Alert>}

      <Collapse in={!login} unmountOnExit>
        <Button
          variant="filled"
          fullWidth
          onClick={handleStart}
          disabled={starting}
          startIcon={starting ? <CircularProgress size={18} color="inherit" /> : <Logo size={22} bare />}
          sx={{
            borderRadius: 3,
            py: 1.25,
            textTransform: 'none',
            fontWeight: 600,
            fontSize: '1rem',
            // Brand gradient — the main, password-free way in.
            background: 'linear-gradient(135deg, #6750A4 0%, #A2457A 100%)',
            color: '#fff',
          }}
        >
          Войти через MAX
        </Button>
        <Typography variant="caption" component="div" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center', mt: 1 }}>
          Без пароля: подтвердите вход одним нажатием в боте JOIN
        </Typography>
      </Collapse>

      <Collapse in={!!login} unmountOnExit>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}>
          <Typography variant="body2" sx={{ color: 'onSurface.main', textAlign: 'center' }}>
            Нажмите <b>«Начать»</b> в боте JOIN в MAX и вернитесь сюда — мы войдём автоматически.
          </Typography>
          {qr && (
            <Box sx={{ p: 1, bgcolor: '#fff', borderRadius: 2, lineHeight: 0 }}>
              <Box component="img" src={qr} alt="QR-код для входа через MAX" sx={{ width: 160, height: 160 }} />
            </Box>
          )}
          <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center' }}>
            С компьютера — отсканируйте QR-код телефоном с MAX.
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'onSurfaceVariant.main' }}>
            <CircularProgress size={14} color="inherit" />
            <Typography variant="caption">Ждём подтверждения из MAX…</Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, width: '100%' }}>
            <Button variant="text" onClick={() => setLogin(null)} sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}>
              Отмена
            </Button>
            <Button
              variant="tonal"
              fullWidth
              onClick={() => login && openMaxDeepLink(login.url)}
              sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
            >
              Открыть MAX ещё раз
            </Button>
          </Box>
        </Box>
      </Collapse>
    </Box>
  );
}
