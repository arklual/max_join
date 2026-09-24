import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import Snackbar from '@mui/material/Snackbar';
import Typography from '@mui/material/Typography';
import { haptic } from '../api/maxBridge';
import { messengerName } from '../api/platform';
import { plural } from '../utils/format';

/** Response of POST /events/{id}/like. */
export interface LikeResult {
  matches: { chatId: number | null; companionId: number; companionName: string | null }[];
  othersInterested: number;
}

interface LikeFeedbackDetail {
  eventTitle: string;
  result: LikeResult | null;
}

const EVENT_NAME = 'join:like-result';

/** Tells the user what happened after a like: a companion found right away, or what happens next. */
export function reportLikeResult(eventTitle: string, result: LikeResult | null): void {
  window.dispatchEvent(new CustomEvent<LikeFeedbackDetail>(EVENT_NAME, { detail: { eventTitle, result } }));
}

function waitingMessage(othersInterested: number): string {
  const where = messengerName();
  const notify = where ? `пришлём напарника в ${where}` : 'пришлём уведомление';
  if (othersInterested > 0) {
    return `Сохранили! Сюда хотят ещё ${othersInterested} ${plural(othersInterested, ['человек', 'человека', 'человек'])} — `
      + `как только найдётся подходящий, ${notify}.`;
  }
  return `Сохранили! Как только кто-то захочет туда же, ${notify}.`;
}

/** Mounted once near the router root; listens for like results from any screen. */
export function LikeFeedbackHost() {
  const navigate = useNavigate();
  const [match, setMatch] = useState<LikeFeedbackDetail | null>(null);
  const [snack, setSnack] = useState('');

  useEffect(() => {
    function onResult(e: Event) {
      const detail = (e as CustomEvent<LikeFeedbackDetail>).detail;
      if (detail.result && detail.result.matches.length > 0) {
        haptic('success');
        setMatch(detail);
      } else {
        haptic('light');
        setSnack(waitingMessage(detail.result?.othersInterested ?? 0));
      }
    }
    window.addEventListener(EVENT_NAME, onResult);
    return () => window.removeEventListener(EVENT_NAME, onResult);
  }, []);

  const first = match?.result?.matches[0];
  const extra = (match?.result?.matches.length ?? 1) - 1;
  const name = first?.companionName || 'Собеседник';
  const firstName = first?.companionName?.trim().split(/\s+/)[0] ?? '';

  function openChat() {
    setMatch(null);
    navigate(first?.chatId ? `/chats/${first.chatId}` : '/chats');
  }

  return (
    <>
      <Dialog open={!!match} onClose={() => setMatch(null)} maxWidth="xs" fullWidth>
        <DialogContent sx={{ textAlign: 'center', pt: 3 }}>
          <Box sx={{ fontSize: 48, lineHeight: 1, mb: 1.5, animation: 'heartPop 0.5s ease-out' }} aria-hidden>
            🎉
          </Box>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
            Напарник найден!
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
            На «{match?.eventTitle}» с тобой хочет пойти <b>{name}</b>
            {extra > 0 ? ` и ещё ${extra} ${plural(extra, ['человек', 'человека', 'человек'])}` : ''}.
            Напиши первым — договоритесь, где встретиться.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setMatch(null)} sx={{ textTransform: 'none' }}>
            Позже
          </Button>
          <Button variant="filled" onClick={openChat} sx={{ textTransform: 'none', borderRadius: 5, fontWeight: 600, flex: 1 }}>
            {firstName ? `Написать ${firstName}` : 'Написать'}
          </Button>
        </DialogActions>
      </Dialog>
      <Snackbar
        open={!!snack}
        autoHideDuration={4500}
        onClose={() => setSnack('')}
        message={snack}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        sx={{ bottom: { xs: 90 } }}
      />
    </>
  );
}
