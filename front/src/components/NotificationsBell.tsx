import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router';
import IconButton from '@mui/material/IconButton';
import Badge from '@mui/material/Badge';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import CircularProgress from '@mui/material/CircularProgress';
import NotificationsNoneOutlined from '@mui/icons-material/NotificationsNoneOutlined';
import ChatBubbleOutlineOutlined from '@mui/icons-material/ChatBubbleOutlineOutlined';
import PersonOutlineOutlined from '@mui/icons-material/PersonOutlineOutlined';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import FavoriteBorderOutlined from '@mui/icons-material/FavoriteBorderOutlined';
import type { SvgIconComponent } from '@mui/icons-material';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import type { AppNotification, NotificationType, PageResponse } from '../types';
import { parseServerDate } from '../utils/dateUtils';

const POLL_INTERVAL_MS = 15000;

const TYPE_ICONS: Record<NotificationType, SvgIconComponent> = {
  MATCH: FavoriteBorderOutlined,
  CHAT_MESSAGE: ChatBubbleOutlineOutlined,
  GROUP_INVITE: GroupsOutlined,
  GENERAL: NotificationsNoneOutlined,
};

/** Most relevant screen for a notification, or null if it has nothing to open. */
function targetPath(n: AppNotification): string | null {
  switch (n.type) {
    case 'MATCH':
      if (n.chatId) return `/chats/${n.chatId}`;
      if (n.matchId) return '/chats';
      if (n.companionId) return `/profile/${n.companionId}`;
      return n.eventId ? `/events/${n.eventId}` : null;
    case 'CHAT_MESSAGE':
      return n.chatId ? `/chats/${n.chatId}` : '/chats';
    case 'GROUP_INVITE':
      if (n.groupId) return `/groups/${n.groupId}`;
      return n.eventId ? `/events/${n.eventId}` : '/groups';
    default:
      if (n.chatId) return `/chats/${n.chatId}`;
      if (n.eventId) return `/events/${n.eventId}`;
      if (n.companionId) return `/profile/${n.companionId}`;
      return null;
  }
}

function formatWhen(iso: string): string {
  if (!iso) return '';
  const d = parseServerDate(iso);
  if (Number.isNaN(d.getTime())) return '';
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMin = Math.round(diffMs / 60000);
  if (diffMin < 1) return 'только что';
  if (diffMin < 60) return `${diffMin} мин назад`;
  const diffH = Math.round(diffMin / 60);
  if (diffH < 24) return `${diffH} ч назад`;
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

export default function NotificationsBell() {
  const navigate = useNavigate();
  const [unread, setUnread] = useState<number>(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const pollTimerRef = useRef<number | null>(null);

  const fetchUnread = useCallback(async () => {
    try {
      const resp = await apiClient.get<{ count: number }>('/notifications/unread-count');
      setUnread(resp.data.count ?? 0);
    } catch {
      // silent
    }
  }, []);

  const fetchList = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
      setError('');
    }
    try {
      const resp = await apiClient.get<PageResponse<AppNotification>>('/notifications', {
        params: { page: 0, size: 30 },
      });
      setItems(resp.data.content ?? []);
      if (silent) setError('');
    } catch {
      // Background refreshes keep the list already on screen.
      if (!silent) setError('Не удалось загрузить уведомления');
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUnread();
    pollTimerRef.current = window.setInterval(fetchUnread, POLL_INTERVAL_MS);
    return () => {
      if (pollTimerRef.current) window.clearInterval(pollTimerRef.current);
    };
  }, [fetchUnread]);

  // While the drawer is open, keep the list itself fresh on the same interval.
  useEffect(() => {
    if (!open) return;
    const t = window.setInterval(() => fetchList(true), POLL_INTERVAL_MS);
    return () => window.clearInterval(t);
  }, [open, fetchList]);

  function handleOpen() {
    setOpen(true);
    fetchList();
  }

  function handleClose() {
    setOpen(false);
  }

  async function markRead(id: number) {
    setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    setUnread((prev) => Math.max(0, prev - 1));
    try {
      await apiClient.post(`/notifications/${id}/read`);
    } catch {
      // soft fail — UI already updated
    }
  }

  async function markAllRead() {
    setItems((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnread(0);
    try {
      await apiClient.post('/notifications/read-all');
    } catch {
      // soft fail
    }
  }

  function openPath(n: AppNotification, path: string | null) {
    if (!n.read) markRead(n.id);
    if (path) {
      setOpen(false);
      navigate(path);
    }
  }

  function handleOpenItem(n: AppNotification) {
    openPath(n, targetPath(n));
  }

  function handleOpenProfile(n: AppNotification) {
    openPath(n, n.companionId ? `/profile/${n.companionId}` : null);
  }

  function handleOpenChat(n: AppNotification) {
    openPath(n, n.chatId ? `/chats/${n.chatId}` : n.matchId ? '/chats' : null);
  }

  return (
    <>
      <IconButton
        onClick={handleOpen}
        aria-label="Уведомления"
        sx={(theme) => ({
          color: theme.palette.onSurface.main,
        })}
      >
        <Badge
          badgeContent={unread}
          color="error"
          overlap="circular"
          max={99}
          invisible={unread === 0}
        >
          <NotificationsNoneOutlined />
        </Badge>
      </IconButton>

      <Drawer
        anchor="right"
        open={open}
        onClose={handleClose}
        PaperProps={{
          sx: {
            width: { xs: '100vw', sm: 380 },
            maxWidth: '100vw',
            pt: 'env(safe-area-inset-top, 0px)',
            pb: 'env(safe-area-inset-bottom, 0px)',
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', p: 1.5, borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Typography variant="h6" noWrap sx={{ fontWeight: 700, minWidth: 0 }}>Уведомления</Typography>
          <Stack direction="row" spacing={0.5} alignItems="center" sx={{ flexShrink: 0 }}>
            <Button
              size="small"
              variant="text"
              onClick={markAllRead}
              disabled={unread === 0}
              sx={{ textTransform: 'none' }}
            >
              Прочитать всё
            </Button>
            <IconButton onClick={handleClose} aria-label="Закрыть">
              <CloseOutlined />
            </IconButton>
          </Stack>
        </Box>

        <Box sx={{ flex: 1, overflow: 'auto', p: 1 }}>
          {loading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={28} />
            </Box>
          )}

          {!loading && error && (
            <Box sx={{ p: 2, textAlign: 'center' }}>
              <Typography variant="body2" color="error">{error}</Typography>
              <Button onClick={() => fetchList()} sx={{ mt: 1, textTransform: 'none' }} size="small">
                Повторить
              </Button>
            </Box>
          )}

          {!loading && !error && items.length === 0 && (
            <Box sx={{ p: 4, textAlign: 'center' }}>
              <NotificationsNoneOutlined sx={{ fontSize: 48, color: 'onSurfaceVariant.main', mb: 1 }} />
              <Typography variant="body2" color="text.secondary">
                Пока нет уведомлений
              </Typography>
            </Box>
          )}

          {!loading && !error && items.length > 0 && (
            <Stack spacing={1}>
              {items.map((n) => {
                const isMatch = n.type === 'MATCH';
                const TypeIcon = TYPE_ICONS[n.type] ?? NotificationsNoneOutlined;
                const hasPersonAvatar = !!(n.companionPhoto || n.companionFirstName);
                return (
                  <Box
                    key={n.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => handleOpenItem(n)}
                    onKeyDown={(e) => {
                      if (e.target !== e.currentTarget) return;
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        handleOpenItem(n);
                      }
                    }}
                    sx={(theme) => ({
                      p: 1.25,
                      borderRadius: 2,
                      cursor: 'pointer',
                      bgcolor: n.read ? 'surfaceContainerLow.main' : 'primaryContainer.main',
                      border: 1,
                      borderColor: n.read ? 'outlineVariant.main' : theme.palette.primary.main,
                      transition: 'opacity 0.15s',
                      '&:active': { opacity: 0.85 },
                      '&:focus-visible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 2 },
                    })}
                  >
                    <Stack direction="row" spacing={1.25} alignItems="flex-start">
                      {hasPersonAvatar ? (
                        <Avatar
                          src={mediaUrl(n.companionPhoto)}
                          alt={n.companionFirstName ?? ''}
                          sx={{
                            width: 44,
                            height: 44,
                            bgcolor: 'primary.main',
                            color: 'onPrimary.main',
                          }}
                        >
                          {(n.companionFirstName ?? '').charAt(0).toUpperCase()}
                        </Avatar>
                      ) : (
                        <Avatar
                          sx={{
                            width: 44,
                            height: 44,
                            bgcolor: 'secondaryContainer.main',
                            color: 'onSecondaryContainer.main',
                          }}
                        >
                          <TypeIcon />
                        </Avatar>
                      )}
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
                          {n.title}
                        </Typography>
                        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mt: 0.25, overflowWrap: 'anywhere' }}>
                          {isMatch && n.companionFirstName && n.eventTitle
                            ? <>Вы совпали с <b>{n.companionFirstName}</b> на «{n.eventTitle}»</>
                            : n.message}
                        </Typography>
                        <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', display: 'block', mt: 0.5 }}>
                          {formatWhen(n.createdAt)}
                        </Typography>
                      </Box>
                    </Stack>

                    {isMatch && (
                      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                        <Button
                          size="small"
                          variant="outlined"
                          startIcon={<PersonOutlineOutlined />}
                          onClick={(e) => { e.stopPropagation(); handleOpenProfile(n); }}
                          disabled={!n.companionId}
                          sx={{ flex: 1, textTransform: 'none', borderRadius: 5 }}
                        >
                          Профиль
                        </Button>
                        <Button
                          size="small"
                          variant="filled"
                          startIcon={<ChatBubbleOutlineOutlined />}
                          onClick={(e) => { e.stopPropagation(); handleOpenChat(n); }}
                          disabled={!n.chatId && !n.matchId}
                          sx={{ flex: 1, textTransform: 'none', borderRadius: 5 }}
                        >
                          Открыть чат
                        </Button>
                      </Stack>
                    )}
                  </Box>
                );
              })}
            </Stack>
          )}
        </Box>

        <Divider />
        <Box sx={{ p: 1, textAlign: 'center' }}>
          <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main' }}>
            Обновляется автоматически каждые 15 сек
          </Typography>
        </Box>
      </Drawer>
    </>
  );
}
