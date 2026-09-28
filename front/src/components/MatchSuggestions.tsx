import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { haptic } from '../api/maxBridge';
import { acceptMatch, declineMatch, errorMessage, inviteMatch } from '../api/matches';
import { formatEventDateTime } from '../utils/dateUtils';
import type { MatchSuggestion } from '../types';

/**
 * "Нашлась компания": companions found for events. Nobody can write before an invitation is accepted —
 * invite ("Позвать пойти вместе"), answer an invitation ("Пойдём" / "Не в этот раз"), or wait for an answer.
 */
export default function MatchSuggestions() {
  const navigate = useNavigate();
  const [items, setItems] = useState<MatchSuggestion[]>([]);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    apiClient
      .get<MatchSuggestion[]>('/matches')
      .then((res) => setItems(res.data ?? []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function act(item: MatchSuggestion, action: (id: number) => Promise<MatchSuggestion>) {
    setBusyId(item.id);
    setError('');
    try {
      const updated = await action(item.id);
      if (updated.status === 'ACCEPTED' && updated.chatId) {
        haptic('success');
        navigate(`/chats/${updated.chatId}`);
        return;
      }
      haptic('light');
      setItems((prev) => (updated.status === 'DECLINED'
        ? prev.filter((i) => i.id !== item.id)
        : prev.map((i) => (i.id === item.id ? updated : i))));
    } catch (err) {
      setError(errorMessage(err, 'Не получилось, попробуйте ещё раз'));
      load();
    } finally {
      setBusyId(null);
    }
  }

  if (items.length === 0) return null;

  return (
    <Box sx={{ mx: 2, mb: 1.5, display: 'flex', flexDirection: 'column', gap: 1 }}>
      <Typography variant="subtitle2" sx={{ color: 'onSurfaceVariant.main', fontWeight: 700 }}>
        Нашлась компания
      </Typography>
      {items.map((item) => {
        const name = item.companionName || 'Собеседник';
        const incoming = item.status === 'REQUESTED' && !item.requestedByMe;
        const outgoing = item.status === 'REQUESTED' && item.requestedByMe;
        const busy = busyId === item.id;
        return (
          <Card
            key={item.id}
            variant="outlined"
            sx={{ p: 1.5, borderRadius: 3, borderColor: incoming ? 'primary.main' : 'outlineVariant.main', borderWidth: incoming ? 1.5 : 1 }}
          >
            <Box
              onClick={() => navigate(`/profile/${item.companionId}`)}
              sx={{ display: 'flex', alignItems: 'center', gap: 1.5, cursor: 'pointer' }}
            >
              <Avatar src={mediaUrl(item.companionPhoto)} alt={name} sx={{ width: 44, height: 44 }}>
                {name.charAt(0)}
              </Avatar>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  {name}{item.companionAge ? `, ${item.companionAge}` : ''}
                </Typography>
                <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {incoming ? 'Зовёт пойти вместе на ' : 'Тоже хочет на '}«{item.eventTitle}»
                  {item.eventDate ? ` · ${formatEventDateTime(item.eventDate, null, 'short')}` : ''}
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: 'flex', gap: 1, mt: 1.25, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
              {incoming && (
                <>
                  <Button size="small" disabled={busy} onClick={() => act(item, declineMatch)} sx={{ textTransform: 'none' }}>
                    Не в этот раз
                  </Button>
                  <Button size="small" variant="filled" disabled={busy} onClick={() => act(item, acceptMatch)} sx={{ textTransform: 'none', borderRadius: 4 }}>
                    Пойдём
                  </Button>
                </>
              )}
              {item.status === 'NEW' && (
                <>
                  <Button size="small" disabled={busy} onClick={() => act(item, declineMatch)} sx={{ textTransform: 'none', color: 'onSurfaceVariant.main' }}>
                    Скрыть
                  </Button>
                  <Button size="small" variant="tonal" disabled={busy} onClick={() => act(item, inviteMatch)} sx={{ textTransform: 'none', borderRadius: 4 }}>
                    Позвать пойти вместе
                  </Button>
                </>
              )}
              {outgoing && (
                <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', alignSelf: 'center' }}>
                  Позвали — чат откроется, когда придёт ответ
                </Typography>
              )}
            </Box>
          </Card>
        );
      })}
      {error && <Alert severity="error">{error}</Alert>}
    </Box>
  );
}
