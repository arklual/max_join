import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import { formatEventDateTime } from '../utils/dateUtils';
import { plural } from '../utils/format';
import type { GroupResponse } from '../types';
import {
  Typography,
  CircularProgress,
  Alert,
  Box,
  Button,
  Card,
  Stack,
  AvatarGroup,
  Avatar,
  LinearProgress,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Snackbar,
} from '@mui/material';
import {
  GroupsOutlined,
  ChatBubbleOutlineOutlined,
  Diversity3,
  ArrowForwardIosOutlined,
} from '@mui/icons-material';

function formatDate(dateString: string | undefined): string {
  return formatEventDateTime(dateString, null, 'short');
}

export default function GroupsScreen() {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [leavingId, setLeavingId] = useState<number | null>(null);
  const [confirmLeaveId, setConfirmLeaveId] = useState<number | null>(null);
  const [snack, setSnack] = useState('');

  const loadGroups = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const resp = await apiClient.get<{ content: GroupResponse[] } | GroupResponse[]>('/groups/my');
      const data = resp.data;
      if (Array.isArray(data)) {
        setGroups(data);
      } else {
        setGroups(data.content ?? []);
      }
    } catch {
      setError('Не удалось загрузить группы. Попробуйте позже.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadGroups();
  }, [loadGroups]);

  async function handleLeave(groupId: number) {
    setConfirmLeaveId(null);
    setLeavingId(groupId);
    try {
      await apiClient.post(`/groups/${groupId}/leave`);
      setGroups((prev) => prev.filter((g) => g.id !== groupId));
    } catch {
      setSnack('Не удалось выйти из группы. Попробуйте ещё раз.');
    } finally {
      setLeavingId(null);
    }
  }

  function handleOpenChat(groupChatId: number | undefined, groupId: number) {
    if (!groupChatId) return;
    navigate(`/group-chats/${groupChatId}`, { state: { groupId } });
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%', p: 2 }}>
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 2, color: 'onSurface.main' }}>
        Мои группы
      </Typography>

      <Card
        variant="outlined"
        onClick={() => navigate('/friend-groups')}
        sx={{
          borderRadius: 3.5,
          mb: 2,
          p: 1.75,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          cursor: 'pointer',
          bgcolor: 'primaryContainer.main',
          color: 'onPrimaryContainer.main',
          '&:active': { opacity: 0.85 },
        }}
      >
        <Diversity3 sx={{ fontSize: 32 }} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            Группы друзей
          </Typography>
          <Typography variant="caption" sx={{ opacity: 0.85, display: 'block' }}>
            Лайкайте мероприятия вместе и находите общие идеи
          </Typography>
        </Box>
        <ArrowForwardIosOutlined sx={{ fontSize: 16, opacity: 0.7 }} />
      </Card>

      {loading && (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, py: 6 }}>
          <CircularProgress />
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
            Загрузка...
          </Typography>
        </Box>
      )}

      {error && !loading && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={loadGroups}>
              Повторить
            </Button>
          }
          sx={{ mt: 2 }}
        >
          {error}
        </Alert>
      )}

      {!loading && !error && groups.length === 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, py: 6, px: 2, textAlign: 'center', animation: 'fadeInUp 0.4s ease-out' }}>
          <GroupsOutlined sx={{ fontSize: 64, color: 'onSurfaceVariant.main' }} />
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', lineHeight: 1.5 }}>
            Вы пока не состоите ни в одной группе.
            <br />
            Найдите мероприятие и создайте компанию!
          </Typography>
          <Button variant="filled" onClick={() => navigate('/afisha')}>
            Перейти в Афишу
          </Button>
        </Box>
      )}

      {!loading && !error && groups.length > 0 && (
        <Stack spacing={1.5} sx={{ pb: 3 }}>
          {groups.map((group, index) => (
            <Card
              key={group.id}
              variant="outlined"
              sx={{
                borderRadius: 3.5,
                overflow: 'hidden',
                animation: 'fadeInUp 0.4s ease-out both',
                animationDelay: `${index * 0.08}s`,
              }}
            >
              <Box sx={{ p: 1.75, display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'onSurface.main', overflowWrap: 'anywhere' }}>
                  {group.title ?? `Группа #${group.id}`}
                </Typography>

                {group.eventTitle && (
                  <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', overflowWrap: 'anywhere' }}>
                    {group.eventTitle}
                    {group.eventDate && (
                      <Typography component="span" variant="body2" sx={{ color: 'outline.main' }}>
                        {' · '}{formatDate(group.eventDate)}
                      </Typography>
                    )}
                  </Typography>
                )}

                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <AvatarGroup max={3} sx={{ '& .MuiAvatar-root': { width: 26, height: 26, fontSize: '0.65rem' } }}>
                    {(group.members ?? []).slice(0, 3).map((m) => (
                      <Avatar
                        key={m.userId}
                        src={mediaUrl(m.photo)}
                        alt={m.firstName}
                        sx={{ bgcolor: 'primary.main', cursor: 'pointer' }}
                        onClick={(e) => { e.stopPropagation(); navigate(`/profile/${m.userId}`); }}
                      >
                        {!m.photo && m.firstName?.charAt(0)}
                      </Avatar>
                    ))}
                  </AvatarGroup>
                  <Typography variant="caption" sx={{ fontWeight: 600, color: 'primary.main' }}>
                    {group.currentSize}/{group.maxSize} {plural(group.maxSize, ['участник', 'участника', 'участников'])}
                  </Typography>
                </Box>

                <LinearProgress
                  variant="determinate"
                  value={group.maxSize > 0 ? Math.min(100, (group.currentSize / group.maxSize) * 100) : 0}
                  sx={{ borderRadius: 1, height: 4 }}
                />

                <Chip
                  size="small"
                  label={
                    group.status === 'OPEN' ? 'Открытая' :
                    group.status === 'FULL' ? 'Заполнена' :
                    'Закрытая'
                  }
                  color={
                    group.status === 'OPEN' ? 'success' :
                    group.status === 'FULL' ? 'warning' :
                    'error'
                  }
                  sx={{ alignSelf: 'flex-start' }}
                />
              </Box>

              <Box
                sx={{
                  display: 'flex',
                  gap: 1,
                  px: 1.75,
                  py: 1.25,
                  bgcolor: 'surfaceContainerLow.main',
                  borderTop: 1,
                  borderColor: 'outlineVariant.main',
                }}
              >
                <Button
                  variant="tonal"
                  startIcon={<ChatBubbleOutlineOutlined />}
                  onClick={() => handleOpenChat(group.groupChatId ?? undefined, group.id)}
                  disabled={!group.groupChatId}
                  sx={{ flex: 1 }}
                >
                  Открыть чат
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  disabled={leavingId === group.id}
                  onClick={() => setConfirmLeaveId(group.id)}
                >
                  {leavingId === group.id ? 'Выходим...' : 'Выйти'}
                </Button>
              </Box>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={confirmLeaveId !== null} onClose={() => setConfirmLeaveId(null)} maxWidth="xs">
        <DialogTitle>Выйти из группы?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Вы покинете группу и её чат.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmLeaveId(null)} sx={{ textTransform: 'none' }}>Отмена</Button>
          <Button
            onClick={() => confirmLeaveId !== null && handleLeave(confirmLeaveId)}
            color="error"
            sx={{ textTransform: 'none' }}
          >
            Выйти
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={!!snack}
        autoHideDuration={3000}
        onClose={() => setSnack('')}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        sx={{ bottom: { xs: 90 } }}
      >
        <Alert severity="error" onClose={() => setSnack('')} sx={{ width: '100%' }}>
          {snack}
        </Alert>
      </Snackbar>
    </Box>
  );
}
