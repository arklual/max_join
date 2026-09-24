import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Card, CircularProgress, Collapse, Typography } from '@mui/material';
import { CheckCircleOutlined, NotificationsActiveOutlined } from '@mui/icons-material';
import QRCode from 'qrcode';
import apiClient from '../api/client';
import { openMaxDeepLink } from '../api/maxBridge';

interface Props {
  /** MAX id already attached to the account, if any. */
  maxId: number | null | undefined;
  /** Called once the account got linked (to refresh the profile). */
  onLinked: () => void;
}

const POLL_MS = 3000;
const LINK_TTL_MS = 15 * 60 * 1000;

const cardSx = {
  borderRadius: 3,
  p: 2,
  borderColor: 'outlineVariant.main',
  animation: 'fadeInUp 0.4s ease-out both',
  animationDelay: '0.21s',
} as const;

/**
 * One-tap MAX linking outside MAX: the user opens a one-time
 * max.ru/<bot>?start=link_<token> link and presses "Start" in the bot.
 */
export default function MaxLinkCard({ maxId, onLinked }: Props) {
  const [url, setUrl] = useState<string | null>(null);
  const [qr, setQr] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [justLinked, setJustLinked] = useState(false);
  const startedAt = useRef(0);

  const checkLinked = useCallback(async () => {
    try {
      const res = await apiClient.get<{ maxId?: number | null }>('/users/me/profile');
      if (res.data.maxId) {
        setUrl(null);
        setJustLinked(true);
        onLinked();
      }
    } catch {
      // try again on the next tick
    }
  }, [onLinked]);

  // While a link is pending, watch for the webhook to attach the account.
  useEffect(() => {
    if (!url) return;
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt.current > LINK_TTL_MS) {
        setUrl(null);
        setError('Ссылка устарела — создайте новую.');
        return;
      }
      void checkLinked();
    }, POLL_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible') void checkLinked();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [url, checkLinked]);

  useEffect(() => {
    if (!url) {
      setQr('');
      return;
    }
    QRCode.toDataURL(url, { margin: 1, width: 360 }).then(setQr).catch(() => setQr(''));
  }, [url]);

  async function handleLink() {
    setError('');
    setLoading(true);
    try {
      const res = await apiClient.post<{ url: string }>('/auth/max-link');
      startedAt.current = Date.now();
      setUrl(res.data.url);
      openMaxDeepLink(res.data.url);
    } catch {
      setError('Не удалось создать ссылку. Проверьте интернет и попробуйте ещё раз.');
    } finally {
      setLoading(false);
    }
  }

  if (maxId) {
    return (
      <Card variant="outlined" sx={cardSx}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{ width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primaryContainer.main', flexShrink: 0 }}>
            <CheckCircleOutlined sx={{ color: 'onPrimaryContainer.main' }} />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 600, fontSize: '0.95rem' }}>MAX привязан</Typography>
            <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
              {justLinked
                ? 'Готово! Уведомления о метчах и сообщениях теперь приходят в MAX.'
                : 'Уведомления о метчах и сообщениях приходят в MAX.'}
            </Typography>
          </Box>
        </Box>
      </Card>
    );
  }

  return (
    <Card variant="outlined" sx={cardSx}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
        <Box sx={{ width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'surfaceContainerHigh.main', flexShrink: 0 }}>
          <NotificationsActiveOutlined sx={{ color: 'primary.main' }} />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ fontWeight: 600, fontSize: '0.95rem' }}>Уведомления в MAX</Typography>
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mt: 0.25 }}>
            Узнавайте о новых метчах и сообщениях сразу. Одно нажатие — и JOIN откроется в MAX с этим же профилем.
          </Typography>
        </Box>
      </Box>

      {error && <Alert severity="warning" sx={{ mt: 2 }}>{error}</Alert>}

      <Collapse in={!url} unmountOnExit>
        <Button
          variant="filled"
          fullWidth
          onClick={handleLink}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
          sx={{ mt: 2, borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
        >
          Привязать MAX
        </Button>
      </Collapse>

      <Collapse in={!!url} unmountOnExit>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, mt: 2 }}>
          <Typography variant="body2" sx={{ color: 'onSurface.main', textAlign: 'center' }}>
            Нажмите <b>«Начать»</b> в боте JOIN в MAX — аккаунт привяжется автоматически.
          </Typography>
          {qr && (
            <Box sx={{ p: 1, bgcolor: '#fff', borderRadius: 2, lineHeight: 0 }}>
              <Box component="img" src={qr} alt="QR-код для привязки MAX" sx={{ width: 160, height: 160 }} />
            </Box>
          )}
          <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center' }}>
            С компьютера — отсканируйте QR-код телефоном. Ссылка одноразовая и действует 15 минут.
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'onSurfaceVariant.main' }}>
            <CircularProgress size={14} color="inherit" />
            <Typography variant="caption">Ждём подтверждения из MAX…</Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, width: '100%' }}>
            <Button variant="text" onClick={() => setUrl(null)} sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}>
              Отмена
            </Button>
            <Button variant="tonal" fullWidth onClick={() => url && openMaxDeepLink(url)} sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}>
              Открыть MAX ещё раз
            </Button>
          </Box>
        </Box>
      </Collapse>
    </Card>
  );
}
