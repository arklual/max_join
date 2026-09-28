import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import apiClient from '../api/client';
import { haptic } from '../api/maxBridge';
import type { OutingState } from '../types';

interface OutingBarProps {
  chatId: number;
  companionName: string | null;
  /** Called with a user-facing error to show as a toast. */
  onError: (message: string) => void;
}

/**
 * After the event of a pair's chat: "Сходили вместе?". Shown only once the event has passed;
 * the bot asks the same the next day.
 */
export default function OutingBar({ chatId, companionName, onError }: OutingBarProps) {
  const [state, setState] = useState<OutingState | null>(null);
  const [busy, setBusy] = useState(false);
  const name = companionName || 'собеседником';

  useEffect(() => {
    apiClient
      .get<OutingState>(`/chats/${chatId}/outing`)
      .then((res) => setState(res.data))
      .catch(() => setState(null));
  }, [chatId]);

  async function answer(went: boolean) {
    setBusy(true);
    try {
      const res = await apiClient.post<OutingState>(`/chats/${chatId}/outing/went`, { went });
      setState(res.data);
      haptic(went ? 'success' : 'light');
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      onError(message ?? 'Не получилось, попробуйте ещё раз');
    } finally {
      setBusy(false);
    }
  }

  if (!state?.eventPassed) return null;

  const done = state.went === 'WENT';
  const text = state.went === 'WENT'
    ? 'Сходили вместе 🎉'
    : state.went === 'NOT_WENT'
      ? 'В этот раз не получилось — в следующий обязательно!'
      : `Сходили вместе с ${name}?`;

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 1,
        px: 2,
        py: 1,
        borderTop: 1,
        borderColor: 'divider',
        bgcolor: done ? 'successContainer.main' : 'surfaceContainerLow.main',
        color: done ? 'onSuccessContainer.main' : 'onSurface.main',
        flexShrink: 0,
      }}
    >
      {done ? (
        <CheckCircleOutlined fontSize="small" sx={{ color: 'success.main' }} />
      ) : (
        <EventAvailableOutlined fontSize="small" sx={{ color: 'primary.main' }} />
      )}
      <Typography variant="body2" sx={{ flex: 1, minWidth: 140, fontWeight: 500 }}>
        {text}
      </Typography>
      {state.went === null && (
        <Box sx={{ display: 'flex', gap: 0.5, flexShrink: 0 }}>
          <Button size="small" variant="filled" disabled={busy} onClick={() => answer(true)} sx={{ textTransform: 'none', borderRadius: 4 }}>
            Да, сходили
          </Button>
          <Button size="small" disabled={busy} onClick={() => answer(false)} sx={{ textTransform: 'none' }}>
            Не получилось
          </Button>
        </Box>
      )}
    </Box>
  );
}
