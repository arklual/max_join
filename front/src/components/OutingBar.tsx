import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CheckCircleOutlined from '@mui/icons-material/CheckCircleOutlined';
import HandshakeOutlined from '@mui/icons-material/HandshakeOutlined';
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
 * A contact is not a plan yet: both press "Договорились", and after the event the pair answers
 * "Сходили вместе?". Sits between the messages and the input of a match chat.
 */
export default function OutingBar({ chatId, companionName, onError }: OutingBarProps) {
  const [state, setState] = useState<OutingState | null>(null);
  const [busy, setBusy] = useState(false);
  const name = companionName || 'собеседник';

  useEffect(() => {
    apiClient
      .get<OutingState>(`/chats/${chatId}/outing`)
      .then((res) => setState(res.data))
      .catch(() => setState(null));
  }, [chatId]);

  async function run(request: () => Promise<{ data: OutingState }>, success?: 'success' | 'light') {
    setBusy(true);
    try {
      const res = await request();
      setState(res.data);
      if (success) haptic(success);
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      onError(message ?? 'Не получилось, попробуйте ещё раз');
    } finally {
      setBusy(false);
    }
  }

  const agree = () => run(() => apiClient.post<OutingState>(`/chats/${chatId}/outing/agree`), 'success');
  const cancel = () => run(() => apiClient.delete<OutingState>(`/chats/${chatId}/outing/agree`), 'light');
  const answer = (went: boolean) =>
    run(() => apiClient.post<OutingState>(`/chats/${chatId}/outing/went`, { went }), went ? 'success' : 'light');

  if (!state) return null;

  let text: string;
  let actions: React.ReactNode = null;
  let done = false;

  if (state.eventPassed) {
    if (state.went === 'WENT') {
      text = 'Сходили вместе 🎉';
      done = true;
    } else if (state.went === 'NOT_WENT') {
      text = 'В этот раз не получилось — в следующий обязательно!';
    } else {
      text = `Сходили вместе с ${name}?`;
      actions = (
        <>
          <Button size="small" variant="filled" disabled={busy} onClick={() => answer(true)} sx={{ textTransform: 'none', borderRadius: 4 }}>
            Да, сходили
          </Button>
          <Button size="small" disabled={busy} onClick={() => answer(false)} sx={{ textTransform: 'none' }}>
            Не получилось
          </Button>
        </>
      );
    }
  } else if (state.agreement === 'AGREED') {
    text = 'Договорились пойти вместе — накануне напомним';
    done = true;
    actions = (
      <Button size="small" disabled={busy} onClick={cancel} sx={{ textTransform: 'none', color: 'onSurfaceVariant.main' }}>
        Отменить
      </Button>
    );
  } else if (state.agreement === 'PROPOSED_BY_COMPANION') {
    text = `${name} предлагает отметить, что вы договорились пойти вместе`;
    actions = (
      <Button size="small" variant="filled" disabled={busy} onClick={agree} sx={{ textTransform: 'none', borderRadius: 4 }}>
        Подтвердить
      </Button>
    );
  } else if (state.agreement === 'PROPOSED_BY_ME') {
    text = `Ждём, когда ${name} подтвердит`;
    actions = (
      <Button size="small" disabled={busy} onClick={cancel} sx={{ textTransform: 'none', color: 'onSurfaceVariant.main' }}>
        Отменить
      </Button>
    );
  } else {
    text = 'Договорились, когда и где встретиться?';
    actions = (
      <Button size="small" variant="tonal" disabled={busy} onClick={agree} sx={{ textTransform: 'none', borderRadius: 4 }}>
        Договорились
      </Button>
    );
  }

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
        <HandshakeOutlined fontSize="small" sx={{ color: 'primary.main' }} />
      )}
      <Typography variant="body2" sx={{ flex: 1, minWidth: 140, fontWeight: 500 }}>
        {text}
      </Typography>
      {actions && <Box sx={{ display: 'flex', gap: 0.5, flexShrink: 0 }}>{actions}</Box>}
    </Box>
  );
}
