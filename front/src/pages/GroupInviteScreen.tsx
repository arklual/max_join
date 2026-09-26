import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import AvatarGroup from '@mui/material/AvatarGroup';
import LinearProgress from '@mui/material/LinearProgress';
import CircularProgress from '@mui/material/CircularProgress';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { formatEventDateTime } from '../utils/dateUtils';
import { plural } from '../utils/format';
import InviteToGroupDialog from '../components/InviteToGroupDialog';
import type { GroupResponse, JoinGroupResponse } from '../types';

/** Landing page of a group invitation link (`group_<id>`): see who is going and join. */
export default function GroupInviteScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [group, setGroup] = useState<GroupResponse | null>(null);
  const [myUserId, setMyUserId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState('');
  const [showInvite, setShowInvite] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      apiClient.get<GroupResponse>(`/groups/${id}`),
      apiClient.get<{ id: number }>('/users/me').catch(() => null),
    ])
      .then(([groupRes, meRes]) => {
        setGroup(groupRes.data);
        setMyUserId(meRes?.data.id ?? null);
      })
      .catch(() => setError('Компания не найдена — возможно, её уже распустили'))
      .finally(() => setLoading(false));
  }, [id]);

  function openChat(groupChatId: number | null | undefined) {
    if (!group) return;
    if (groupChatId) navigate(`/group-chats/${groupChatId}`, { state: { groupId: group.id }, replace: true });
    else navigate('/groups', { replace: true });
  }

  async function handleJoin() {
    if (!group) return;
    setJoining(true);
    setJoinError('');
    try {
      const resp = await apiClient.post<JoinGroupResponse>(`/groups/${group.id}/join`);
      openChat(resp.data.groupChatId);
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setJoinError(message ?? 'Не удалось присоединиться');
    } finally {
      setJoining(false);
    }
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !group) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, py: 6, px: 2 }}>
        <Alert severity="error" sx={{ width: '100%', maxWidth: 360 }}>{error || 'Компания не найдена'}</Alert>
        <Button variant="outlined" onClick={() => navigate('/afisha', { replace: true })} sx={{ textTransform: 'none', borderRadius: 5 }}>
          В афишу
        </Button>
      </Box>
    );
  }

  const isMember = myUserId != null && group.members.some((m) => m.userId === myUserId);
  const freeSpots = Math.max(0, group.maxSize - group.currentSize);
  const isOpen = group.status === 'OPEN' && freeSpots > 0;
  const creatorName = group.creator?.firstName;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, p: 2, animation: 'fadeInUp 0.4s ease-out' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'primary.main' }}>
        <GroupsOutlined />
        <Typography variant="subtitle2">
          {isMember ? 'Ваша компания' : creatorName ? `${creatorName} зовёт в компанию` : 'Приглашение в компанию'}
        </Typography>
      </Box>

      <Card variant="outlined" sx={{ borderRadius: 3.5, p: 2, display: 'flex', flexDirection: 'column', gap: 1.25 }}>
        <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.3, overflowWrap: 'anywhere' }}>
          {group.eventTitle ?? 'Событие'}
        </Typography>
        {group.eventDate && (
          <Typography variant="body2" color="text.secondary">
            {formatEventDateTime(group.eventDate, null)}
          </Typography>
        )}
        {group.title && (
          <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
            «{group.title}»
          </Typography>
        )}
        {group.description && (
          <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {group.description}
          </Typography>
        )}

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 0.5 }}>
          <AvatarGroup max={5} sx={{ '& .MuiAvatar-root': { width: 32, height: 32, fontSize: '0.8rem' } }}>
            {group.members.map((m) => (
              <Avatar key={m.userId} src={mediaUrl(m.photo)} alt={m.firstName}>
                {m.firstName?.charAt(0)}
              </Avatar>
            ))}
          </AvatarGroup>
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', minWidth: 0 }}>
            {group.members.map((m) => m.firstName).filter(Boolean).join(', ')}
          </Typography>
        </Box>

        <LinearProgress
          variant="determinate"
          value={group.maxSize > 0 ? Math.min(100, (group.currentSize / group.maxSize) * 100) : 0}
          sx={{ borderRadius: 1, height: 4 }}
        />
        <Typography variant="caption" sx={{ fontWeight: 600, color: 'primary.main' }}>
          {group.currentSize}/{group.maxSize} · {freeSpots > 0
            ? `свободно ${freeSpots} ${plural(freeSpots, ['место', 'места', 'мест'])}`
            : 'мест нет'}
        </Typography>
      </Card>

      {joinError && <Alert severity="error">{joinError}</Alert>}

      {isMember ? (
        <>
          <Button variant="filled" onClick={() => openChat(group.groupChatId)} sx={{ textTransform: 'none', borderRadius: 6, py: 1.2, fontWeight: 600 }}>
            Открыть чат компании
          </Button>
          <Button variant="tonal" onClick={() => setShowInvite(true)} disabled={!isOpen} sx={{ textTransform: 'none', borderRadius: 6, py: 1 }}>
            Позвать ещё друзей
          </Button>
        </>
      ) : (
        <Button
          variant="filled"
          onClick={handleJoin}
          disabled={!isOpen || joining}
          sx={{ textTransform: 'none', borderRadius: 6, py: 1.2, fontWeight: 600 }}
        >
          {!isOpen ? 'Мест больше нет' : joining ? 'Присоединяемся...' : 'Присоединиться'}
        </Button>
      )}

      <Button variant="text" onClick={() => navigate(`/events/${group.eventId}`)} sx={{ textTransform: 'none' }}>
        Подробнее о событии
      </Button>

      {showInvite && (
        <InviteToGroupDialog
          groupId={group.id}
          eventTitle={group.eventTitle}
          eventDate={group.eventDate}
          freeSpots={freeSpots}
          onClose={() => setShowInvite(false)}
        />
      )}
    </Box>
  );
}
