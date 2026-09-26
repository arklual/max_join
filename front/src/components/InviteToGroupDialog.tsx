import { useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import ShareOutlined from '@mui/icons-material/ShareOutlined';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { loadAppConfig } from '../api/appConfig';
import { buildGroupInviteLink } from '../utils/startTarget';
import { formatEventDateTime } from '../utils/dateUtils';
import { shareLink } from '../utils/share';
import { plural } from '../utils/format';
import type { GroupInviteCandidate, GroupInviteStatus } from '../types';

interface InviteToGroupDialogProps {
  groupId: number;
  eventTitle?: string | null;
  eventDate?: string | null;
  freeSpots: number;
  onClose: () => void;
}

const STATUS_LABELS: Record<Exclude<GroupInviteStatus, 'AVAILABLE'>, string> = {
  INVITED: 'Позвали',
  IN_GROUP: 'Уже в компании',
  BUSY: 'Идёт с другими',
};

/**
 * "Идём с другом — и с кем-то ещё": invite a friend into an event group,
 * either by a link to any chat or directly one of the people you already know in JOIN.
 */
export default function InviteToGroupDialog({ groupId, eventTitle, eventDate, freeSpots, onClose }: InviteToGroupDialogProps) {
  const [candidates, setCandidates] = useState<GroupInviteCandidate[] | null>(null);
  const [invitingId, setInvitingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  useEffect(() => {
    apiClient
      .get<GroupInviteCandidate[]>(`/groups/${groupId}/invite-candidates`)
      .then((res) => setCandidates(res.data ?? []))
      .catch(() => setCandidates([]));
  }, [groupId]);

  async function handleShareLink() {
    setError('');
    const { maxBotUsername } = await loadAppConfig();
    const link = buildGroupInviteLink(groupId, maxBotUsername);
    const when = formatEventDateTime(eventDate, null);
    const text = eventTitle
      ? `Пошли со мной на «${eventTitle}»${when ? ` — ${when}` : ''}! Собираю компанию в JOIN — присоединяйся:`
      : 'Присоединяйся к моей компании в JOIN:';
    const result = await shareLink(eventTitle ?? 'JOIN', text, link);
    if (result === 'copied') setInfo('Ссылка скопирована — отправь её другу');
    if (result === 'failed') setError('Не удалось поделиться ссылкой');
  }

  async function handleInvite(userId: number) {
    setInvitingId(userId);
    setError('');
    try {
      await apiClient.post(`/groups/${groupId}/invite`, { userId });
      setCandidates((prev) => prev?.map((c) => (c.userId === userId ? { ...c, status: 'INVITED' } : c)) ?? prev);
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(message ?? 'Не удалось отправить приглашение');
    } finally {
      setInvitingId(null);
    }
  }

  const noSpots = freeSpots <= 0;

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Позвать в компанию</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography variant="body2" color="text.secondary">
          {noSpots
            ? 'В компании не осталось мест — увеличить её пока нельзя.'
            : `Свободно ${freeSpots} ${plural(freeSpots, ['место', 'места', 'мест'])}: позовите друзей, а остальные места займут новые знакомые.`}
        </Typography>

        <Button
          variant="filled"
          startIcon={<ShareOutlined />}
          onClick={handleShareLink}
          disabled={noSpots}
          sx={{ textTransform: 'none', borderRadius: 6, py: 1, fontWeight: 600 }}
        >
          Отправить ссылку
        </Button>

        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Знакомые в JOIN
          </Typography>
          {candidates === null && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              {[0, 1].map((i) => <Skeleton key={i} variant="rounded" height={44} sx={{ borderRadius: 2 }} />)}
            </Box>
          )}
          {candidates?.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              Здесь появятся напарники из чатов и участники ваших групп друзей. Пока отправьте другу ссылку.
            </Typography>
          )}
          {candidates && candidates.length > 0 && (
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
              {candidates.map((c) => (
                <Box component="li" key={c.userId} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Avatar src={mediaUrl(c.photo)} alt={c.firstName} sx={{ width: 36, height: 36 }}>
                    {c.firstName?.charAt(0)}
                  </Avatar>
                  <Typography variant="body2" sx={{ flex: 1, minWidth: 0, fontWeight: 600, overflowWrap: 'anywhere' }}>
                    {c.firstName}
                  </Typography>
                  {c.status === 'AVAILABLE' ? (
                    <Button
                      variant="tonal"
                      size="small"
                      disabled={noSpots || invitingId === c.userId}
                      onClick={() => handleInvite(c.userId)}
                      sx={{ textTransform: 'none', borderRadius: 4, flexShrink: 0 }}
                    >
                      {invitingId === c.userId ? 'Зовём...' : 'Позвать'}
                    </Button>
                  ) : (
                    <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                      {STATUS_LABELS[c.status]}
                    </Typography>
                  )}
                </Box>
              ))}
            </Box>
          )}
        </Box>

        {error && <Alert severity="error">{error}</Alert>}
        {info && <Alert severity="success">{info}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} sx={{ textTransform: 'none' }}>
          Готово
        </Button>
      </DialogActions>
    </Dialog>
  );
}
