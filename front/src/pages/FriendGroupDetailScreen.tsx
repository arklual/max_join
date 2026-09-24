import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  Typography,
  CircularProgress,
  Alert,
  Box,
  Button,
  Card,
  Stack,
  Avatar,
  AppBar,
  Toolbar,
  IconButton,
  Chip,
  Snackbar,
  Divider,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  ArrowBackOutlined,
  ContentCopyOutlined,
  ChatBubbleOutlineOutlined,
  ExitToAppOutlined,
  EventOutlined,
  QrCode2Outlined,
  ShareOutlined,
} from '@mui/icons-material';
import QRCode from 'qrcode';
import apiClient from '../api/client';
import { isNativeApp, isTelegramApp, mediaUrl } from '../api/platform';
import { getTelegramWebApp } from '../api/telegramBridge';
import { nativeCopy, nativeShare } from '../api/native';
import { formatEventDateTime } from '../utils/dateUtils';
import { useAppConfig } from '../hooks/useAppConfig';
import { buildFriendGroupInviteUrl } from '../utils/inviteLinks';
import { getInitDataRaw, getMaxWebApp } from '../api/maxBridge';
import type {
  FriendGroup,
  PageResponse,
  EventCard as EventCardType,
} from '../types';

interface AxiosErrorLike {
  response?: { status?: number; data?: { message?: string } };
}

function formatDate(dateString: string | undefined | null): string {
  return formatEventDateTime(dateString, null, 'short');
}

const COPY_FAILED_MESSAGE = 'Не удалось скопировать — выделите и скопируйте вручную';

/** Copies text to the clipboard; falls back to execCommand for WebViews without the async Clipboard API. */
async function copyText(text: string): Promise<boolean> {
  if (await nativeCopy(text)) return true;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // fall through to the legacy path
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.top = '0';
    ta.style.left = '0';
    ta.style.opacity = '0';
    ta.style.pointerEvents = 'none';
    document.body.appendChild(ta);
    ta.select();
    ta.setSelectionRange(0, text.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

export default function FriendGroupDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const groupId = Number(id);

  const [group, setGroup] = useState<FriendGroup | null>(null);
  const [commonEvents, setCommonEvents] = useState<EventCardType[]>([]);
  const [loading, setLoading] = useState(true);
  const [eventsLoading, setEventsLoading] = useState(false);
  const [error, setError] = useState('');
  const [leaving, setLeaving] = useState(false);
  const [snack, setSnack] = useState('');
  const [confirmLeaveOpen, setConfirmLeaveOpen] = useState(false);
  const [qrDataUrl, setQrDataUrl] = useState<string>('');
  const qrAbortRef = useRef<boolean>(false);
  const appConfig = useAppConfig();
  // Invites point at the messenger the user is in (Telegram → t.me bot, otherwise MAX).
  const inviteMessenger = isTelegramApp() ? 'telegram' : 'max';
  const botUsername = (inviteMessenger === 'telegram' ? appConfig?.telegramBotUsername : appConfig?.maxBotUsername) ?? '';
  const inviteShareUrl = group?.inviteCode
    ? buildFriendGroupInviteUrl(group.inviteCode, botUsername || null, inviteMessenger)
    : '';
  const maxApp = getMaxWebApp();
  const canShare = isNativeApp() || isTelegramApp()
    || (!!getInitDataRaw() && typeof maxApp?.shareMaxContent === 'function');

  const loadGroup = useCallback(async () => {
    if (isNaN(groupId)) {
      setError('Некорректный ID группы');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const resp = await apiClient.get<FriendGroup>(`/friend-groups/${groupId}`);
      setGroup(resp.data);
    } catch (err) {
      const axiosErr = err as AxiosErrorLike;
      if (axiosErr.response?.status === 403) {
        setError('Вы не состоите в этой группе.');
      } else if (axiosErr.response?.status === 404) {
        setError('Группа не найдена.');
      } else {
        setError('Не удалось загрузить группу.');
      }
    } finally {
      setLoading(false);
    }
  }, [groupId]);

  const loadCommonEvents = useCallback(async () => {
    if (isNaN(groupId)) return;
    setEventsLoading(true);
    try {
      const resp = await apiClient.get<PageResponse<EventCardType>>(
        `/friend-groups/${groupId}/common-events`,
        { params: { page: 0, size: 20 } },
      );
      setCommonEvents(resp.data.content ?? []);
    } catch {
      // soft fail — not critical
    } finally {
      setEventsLoading(false);
    }
  }, [groupId]);

  useEffect(() => {
    loadGroup();
  }, [loadGroup]);

  useEffect(() => {
    if (group) loadCommonEvents();
  }, [group, loadCommonEvents]);

  useEffect(() => {
    if (!group?.inviteCode || !inviteShareUrl) {
      setQrDataUrl('');
      return;
    }
    qrAbortRef.current = false;
    QRCode.toDataURL(inviteShareUrl, { margin: 1, width: 240, errorCorrectionLevel: 'M' })
      .then((url) => {
        if (!qrAbortRef.current) setQrDataUrl(url);
      })
      .catch(() => {
        if (!qrAbortRef.current) setQrDataUrl('');
      });
    return () => {
      qrAbortRef.current = true;
    };
  }, [group?.inviteCode, inviteShareUrl]);

  async function handleCopyInvite() {
    if (!group?.inviteCode) return;
    const ok = await copyText(group.inviteCode);
    setSnack(ok ? 'Код скопирован' : COPY_FAILED_MESSAGE);
  }

  async function handleShareLink() {
    if (!inviteShareUrl) return;
    if (canShare) {
      try {
        if (isNativeApp()) {
          if (await nativeShare({ title: 'Приглашение в JOIN', text: `Присоединяйся к моей группе в JOIN! Код: ${group?.inviteCode}`, url: inviteShareUrl })) return;
          throw new Error('share unavailable');
        }
        if (isTelegramApp()) {
          const share = `https://t.me/share/url?url=${encodeURIComponent(inviteShareUrl)}&text=${encodeURIComponent('Присоединяйся к моей группе в JOIN!')}`;
          const tg = getTelegramWebApp();
          if (tg?.openTelegramLink) {
            tg.openTelegramLink(share);
            return;
          }
          throw new Error('share unavailable');
        }
        await maxApp?.shareMaxContent?.({ text: 'Присоединяйся к моей группе в JOIN', link: inviteShareUrl });
        return;
      } catch {
        // share sheet unavailable/cancelled — fall back to copying
      }
    }
    const ok = await copyText(inviteShareUrl);
    setSnack(ok ? 'Ссылка скопирована' : COPY_FAILED_MESSAGE);
  }

  async function handleLeave() {
    setConfirmLeaveOpen(false);
    setLeaving(true);
    try {
      await apiClient.delete(`/friend-groups/${groupId}/leave`);
      navigate('/friend-groups');
    } catch {
      setSnack('Не удалось выйти. Попробуйте ещё раз.');
    } finally {
      setLeaving(false);
    }
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Toolbar>
            <IconButton edge="start" onClick={() => navigate('/friend-groups')} sx={{ color: 'primary.main' }} aria-label="Назад">
              <ArrowBackOutlined />
            </IconButton>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
              Загрузка...
            </Typography>
          </Toolbar>
        </AppBar>
        <Box sx={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center' }}>
          <CircularProgress />
        </Box>
      </Box>
    );
  }

  if (error || !group) {
    return (
      <Box>
        <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
          <Toolbar>
            <IconButton edge="start" onClick={() => navigate('/friend-groups')} sx={{ color: 'primary.main' }} aria-label="Назад">
              <ArrowBackOutlined />
            </IconButton>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
              Ошибка
            </Typography>
          </Toolbar>
        </AppBar>
        <Alert
          severity="error"
          sx={{ m: 2 }}
          action={
            !isNaN(groupId) && (
              <Button color="inherit" size="small" onClick={loadGroup} sx={{ textTransform: 'none' }}>
                Повторить
              </Button>
            )
          }
        >
          {error || 'Не удалось загрузить группу'}
        </Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
      <AppBar position="static" color="transparent" elevation={0} sx={{ borderBottom: 1, borderColor: 'outlineVariant.main' }}>
        <Toolbar>
          <IconButton edge="start" onClick={() => navigate('/friend-groups')} sx={{ color: 'primary.main' }} aria-label="Назад">
            <ArrowBackOutlined />
          </IconButton>
          <Typography noWrap variant="subtitle1" sx={{ fontWeight: 600, color: 'onSurface.main', flex: 1, minWidth: 0 }}>
            {group.name}
          </Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {/* Invite code + QR */}
        <Card variant="outlined" sx={{ borderRadius: 3, p: 1.75 }}>
          <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', textTransform: 'uppercase', letterSpacing: 1 }}>
            Приглашение в группу
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.75 }}>
            <Typography
              variant="h5"
              sx={{ fontWeight: 700, fontFamily: 'monospace', flex: 1, minWidth: 0, wordBreak: 'break-all', color: 'primary.main', userSelect: 'all' }}
            >
              {group.inviteCode}
            </Typography>
            <IconButton onClick={handleCopyInvite} sx={{ color: 'primary.main', flexShrink: 0 }} aria-label="Скопировать код">
              <ContentCopyOutlined />
            </IconButton>
          </Box>
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 1,
              mt: 1.5,
              pt: 1.5,
              borderTop: 1,
              borderColor: 'outlineVariant.main',
            }}
          >
            {qrDataUrl ? (
              <Box
                component="img"
                src={qrDataUrl}
                alt="QR-код приглашения"
                sx={{
                  width: 200,
                  height: 200,
                  maxWidth: '100%',
                  borderRadius: 2,
                  bgcolor: 'common.white',
                  p: 1,
                  boxShadow: 1,
                }}
              />
            ) : (
              <Box
                sx={{
                  width: 200,
                  height: 200,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: 'surfaceContainerLow.main',
                  borderRadius: 2,
                }}
              >
                <QrCode2Outlined sx={{ fontSize: 64, color: 'onSurfaceVariant.main' }} />
              </Box>
            )}
            <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center', maxWidth: 280 }}>
              Друг может отсканировать QR-код в любом приложении камеры — он сразу откроет
              JOIN в {inviteMessenger === 'telegram' ? 'Telegram' : 'MAX'}. Или ввести код руками — как удобнее.
            </Typography>
            {inviteShareUrl && (
              <Button
                variant="text"
                size="small"
                startIcon={canShare ? <ShareOutlined /> : <ContentCopyOutlined />}
                onClick={handleShareLink}
                sx={{ textTransform: 'none', mt: 0.5 }}
              >
                {canShare ? 'Поделиться ссылкой' : 'Скопировать ссылку'}
              </Button>
            )}
          </Box>
        </Card>

        {/* Members */}
        <Card variant="outlined" sx={{ borderRadius: 3 }}>
          <Box sx={{ p: 1.75, pb: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
              Участники ({group.currentSize}/{group.maxSize})
            </Typography>
          </Box>
          <List dense>
            {group.members.map((m) => (
              <ListItem
                key={m.userId}
                onClick={() => navigate(`/profile/${m.userId}`)}
                sx={{ cursor: 'pointer', '&:active': { bgcolor: 'action.hover' } }}
              >
                <ListItemAvatar>
                  <Avatar src={mediaUrl(m.photo)} alt={m.firstName ?? ''} sx={{ bgcolor: 'primary.main' }}>
                    {!m.photo && (m.firstName ?? '?').charAt(0).toUpperCase()}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={m.firstName ?? '—'}
                  primaryTypographyProps={{ variant: 'body2', color: 'onSurface.main', noWrap: true }}
                  sx={{ minWidth: 0 }}
                />
                {m.role === 'CREATOR' && (
                  <Chip size="small" label="Создатель" color="primary" variant="outlined" sx={{ fontSize: '0.7rem', height: 22, flexShrink: 0, ml: 1 }} />
                )}
              </ListItem>
            ))}
          </List>
        </Card>

        {/* Common Events */}
        <Card variant="outlined" sx={{ borderRadius: 3, p: 1.75 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'onSurface.main', mb: 1 }}>
            Куда хотят все
          </Typography>
          {eventsLoading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
              <CircularProgress size={24} />
            </Box>
          )}
          {!eventsLoading && commonEvents.length === 0 && (
            <Stack direction="row" spacing={1.5} alignItems="center" sx={{ py: 1 }}>
              <EventOutlined sx={{ color: 'onSurfaceVariant.main' }} />
              <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', flex: 1 }}>
                Пока нет общих лайков. Каждый из вас должен лайкнуть одно и то же событие — и оно появится здесь.
              </Typography>
            </Stack>
          )}
          {!eventsLoading && commonEvents.length > 0 && (
            <Stack spacing={1} sx={{ mt: 0.5 }}>
              {commonEvents.map((e) => (
                <Box
                  key={e.id}
                  onClick={() => navigate(`/events/${e.id}`)}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1.25,
                    py: 1,
                    cursor: 'pointer',
                    '&:active': { bgcolor: 'action.hover' },
                  }}
                >
                  <Box
                    sx={{
                      width: 48,
                      height: 48,
                      borderRadius: 2,
                      bgcolor: 'surfaceContainerHigh.main',
                      backgroundImage: e.imageUrl ? `url(${e.imageUrl})` : undefined,
                      backgroundSize: 'cover',
                      backgroundPosition: 'center',
                      flexShrink: 0,
                    }}
                  />
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography
                      variant="body2"
                      sx={{ fontWeight: 600, color: 'onSurface.main', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                    >
                      {e.title}
                    </Typography>
                    <Typography variant="caption" noWrap sx={{ color: 'onSurfaceVariant.main', display: 'block' }}>
                      {[formatDate(e.eventDate), e.city].filter(Boolean).join(' · ')}
                    </Typography>
                  </Box>
                </Box>
              ))}
            </Stack>
          )}
        </Card>

        <Divider />

        <Stack spacing={1}>
          <Button
            variant="filled"
            startIcon={<ChatBubbleOutlineOutlined />}
            onClick={() => navigate(`/friend-groups/${groupId}/chat`)}
          >
            Открыть чат
          </Button>
          <Button
            variant="outlined"
            color="error"
            startIcon={<ExitToAppOutlined />}
            onClick={() => setConfirmLeaveOpen(true)}
            disabled={leaving}
          >
            {leaving ? 'Выходим...' : 'Покинуть группу'}
          </Button>
        </Stack>
      </Box>

      <Dialog open={confirmLeaveOpen} onClose={() => setConfirmLeaveOpen(false)} maxWidth="xs">
        <DialogTitle>Покинуть группу?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Вы выйдете из группы «{group.name}» и её чата.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmLeaveOpen(false)} sx={{ textTransform: 'none' }}>Отмена</Button>
          <Button onClick={handleLeave} color="error" sx={{ textTransform: 'none' }}>Покинуть</Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={!!snack}
        autoHideDuration={snack === COPY_FAILED_MESSAGE ? 4000 : 2000}
        onClose={() => setSnack('')}
        message={snack}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        sx={{ bottom: { xs: 90 } }}
      />
    </Box>
  );
}
