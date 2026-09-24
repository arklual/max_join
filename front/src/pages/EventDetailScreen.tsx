import { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router';
import apiClient from '../api/client';
import { mediaUrl } from '../api/platform';
import {
  INTEREST_LABELS,
  type InterestType,
  type EventDetail,
  type EventGroup,
  type CreateGroupRequest,
  type JoinGroupResponse,
} from '../types';

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Chip from '@mui/material/Chip';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Slider from '@mui/material/Slider';
import Alert from '@mui/material/Alert';
import AvatarGroup from '@mui/material/AvatarGroup';
import Avatar from '@mui/material/Avatar';
import LinearProgress from '@mui/material/LinearProgress';
import Skeleton from '@mui/material/Skeleton';
import CircularProgress from '@mui/material/CircularProgress';
import Snackbar from '@mui/material/Snackbar';
import ContentCopyOutlined from '@mui/icons-material/ContentCopyOutlined';
import LinkifiedText from '../components/LinkifiedText';
import PushkinCardInfo from '../components/PushkinCardInfo';
import { openExternalLink } from '../api/maxBridge';
import { nativeCopy } from '../api/native';
import { formatPrice } from '../utils/format';
import { formatEventDateTime } from '../utils/dateUtils';

import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined';
import FavoriteOutlined from '@mui/icons-material/FavoriteOutlined';
import FavoriteBorderOutlined from '@mui/icons-material/FavoriteBorderOutlined';
import AddOutlined from '@mui/icons-material/AddOutlined';
import { getTagChipSx } from '../components/tagChipStyles';

// ── Create Group Modal ────────────────────────────────────────────────────────

interface CreateGroupModalProps {
  eventId: number;
  onClose: () => void;
  onCreated: (group: EventGroup) => void;
}

function CreateGroupModal({ eventId, onClose, onCreated }: CreateGroupModalProps) {
  const [title, setTitle] = useState('');
  const [maxSize, setMaxSize] = useState(5);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError('');

    const payload: CreateGroupRequest = {
      maxSize,
      ...(title.trim() ? { title: title.trim() } : {}),
    };

    try {
      const resp = await apiClient.post<EventGroup>(`/events/${eventId}/groups`, payload);
      onCreated(resp.data);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string }; status?: number } };
      if (axiosErr.response?.status === 409) {
        setError('Вы уже состоите в группе на это мероприятие');
      } else if (axiosErr.response?.status === 422) {
        setError('Нельзя создать группу на прошедшее мероприятие');
      } else {
        setError(axiosErr.response?.data?.message ?? 'Не удалось создать группу');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Создать группу</DialogTitle>
      <DialogContent>
        <Box component="form" id="create-group-form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 1 }}>
          <TextField
            label="Название группы (необязательно)"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Например: Идём вместе!"
            inputProps={{ maxLength: 100 }}
            fullWidth
            variant="outlined"
          />

          <Box>
            <Typography variant="body2" sx={{ mb: 1 }}>
              Максимальный размер: <strong>{maxSize}</strong> человек
            </Typography>
            <Slider
              value={maxSize}
              onChange={(_, value) => setMaxSize(value as number)}
              min={3}
              max={20}
              step={1}
              marks={[
                { value: 3, label: '3' },
                { value: 20, label: '20' },
              ]}
              valueLabelDisplay="auto"
            />
          </Box>

          {error && <Alert severity="error">{error}</Alert>}
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} variant="text" sx={{ textTransform: 'none' }}>
          Отмена
        </Button>
        <Button
          type="submit"
          form="create-group-form"
          variant="filled"
          disabled={submitting}
          sx={{ textTransform: 'none' }}
        >
          {submitting ? 'Создаём...' : 'Создать группу'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ── Groups Section ────────────────────────────────────────────────────────────

interface GroupsSectionProps {
  eventId: number;
}

function GroupsSection({ eventId }: GroupsSectionProps) {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<EventGroup[]>([]);
  const [totalGroups, setTotalGroups] = useState(0);
  const [myUserId, setMyUserId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [joiningId, setJoiningId] = useState<number | null>(null);
  const [joinError, setJoinError] = useState('');

  useEffect(() => {
    fetchGroups();
  }, [eventId]);

  useEffect(() => {
    apiClient
      .get<{ id: number }>('/users/me')
      .then((res) => setMyUserId(res.data.id))
      .catch(() => {});
  }, []);

  async function fetchGroups() {
    setLoading(true);
    setError('');
    try {
      const resp = await apiClient.get<{
        content: EventGroup[];
        totalElements: number;
      }>(`/events/${eventId}/groups`, { params: { size: 3 } });
      setGroups(resp.data.content ?? []);
      setTotalGroups(resp.data.totalElements ?? resp.data.content?.length ?? 0);
    } catch {
      setError('Не удалось загрузить группы. Попробуйте ещё раз.');
    } finally {
      setLoading(false);
    }
  }

  async function handleJoin(groupId: number) {
    setJoiningId(groupId);
    setJoinError('');
    try {
      const resp = await apiClient.post<JoinGroupResponse>(`/groups/${groupId}/join`);
      openGroupChat(resp.data.groupId, resp.data.groupChatId);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string }; status?: number } };
      if (axiosErr.response?.status === 409) {
        setJoinError('Вы уже состоите в группе на это мероприятие');
      } else {
        setJoinError(axiosErr.response?.data?.message ?? 'Не удалось вступить в группу');
      }
    } finally {
      setJoiningId(null);
    }
  }

  function openGroupChat(groupId: number, groupChatId: number | undefined) {
    if (groupChatId) {
      navigate(`/group-chats/${groupChatId}`, { state: { groupId } });
    } else {
      navigate('/groups');
    }
  }

  function handleGroupCreated(group: EventGroup) {
    setShowModal(false);
    openGroupChat(group.id, group.groupChatId);
  }

  return (
    <Box sx={{ mt: 1, pt: 2, borderTop: 1, borderColor: 'divider' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 1.5 }}>
        <Typography variant="h6" sx={{ fontSize: '1.1rem', whiteSpace: 'nowrap', minWidth: 0 }}>
          {loading ? 'Открытые группы' : `Открытые группы${totalGroups ? ` (${totalGroups})` : ''}`}
        </Typography>
        <Button
          variant="tonal"
          startIcon={<AddOutlined />}
          onClick={() => setShowModal(true)}
          sx={{ textTransform: 'none', borderRadius: 5 }}
        >
          Создать
        </Button>
      </Box>

      {loading && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {[0, 1].map((i) => (
            <Skeleton key={i} variant="rounded" height={80} sx={{ borderRadius: 3 }} />
          ))}
        </Box>
      )}

      {error && !loading && (
        <Box sx={{ textAlign: 'center', py: 1.5 }}>
          <Typography variant="body2" color="error" sx={{ mb: 1 }}>
            {error}
          </Typography>
          <Button variant="outlined" onClick={fetchGroups} sx={{ textTransform: 'none', borderRadius: 4 }}>
            Повторить
          </Button>
        </Box>
      )}

      {!loading && !error && groups.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 2 }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            Станьте первым — создайте компанию.
          </Typography>
          <Button
            variant="filled"
            onClick={() => setShowModal(true)}
            sx={{ textTransform: 'none', borderRadius: 6 }}
          >
            Создать группу
          </Button>
        </Box>
      )}

      {!loading && !error && groups.length > 0 && (
        <>
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 1.5, pb: 2 }}>
            {groups.map((group, index) => (
              <Card
                key={group.id}
                variant="outlined"
                component="li"
                sx={{
                  p: 1.5,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                  borderRadius: 3,
                  animation: 'fadeInUp 0.35s ease-out both',
                  animationDelay: `${index * 0.08}s`,
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
                  <Typography variant="subtitle2" sx={{ minWidth: 0, flex: 1, overflowWrap: 'anywhere' }}>
                    {group.title ?? `Группа #${group.id}`}
                  </Typography>
                  <Typography variant="caption" color="primary" fontWeight={600} sx={{ flexShrink: 0, whiteSpace: 'nowrap', lineHeight: 1.6 }}>
                    {group.currentSize}/{group.maxSize} чел.
                  </Typography>
                </Box>

                {group.description && (
                  <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                    {group.description}
                  </Typography>
                )}

                {group.members && group.members.length > 0 && (
                  <AvatarGroup max={4} sx={{ justifyContent: 'flex-end', flexDirection: 'row' }}>
                    {group.members.map((member) => (
                      <Avatar key={member.userId} src={mediaUrl(member.photo)} alt={member.firstName} sx={{ width: 28, height: 28, fontSize: '0.75rem' }}>
                        {member.firstName.charAt(0)}
                      </Avatar>
                    ))}
                  </AvatarGroup>
                )}

                <LinearProgress
                  variant="determinate"
                  value={(group.currentSize / group.maxSize) * 100}
                  sx={{ borderRadius: 1, height: 4 }}
                />

                {myUserId != null && group.members?.some((m) => m.userId === myUserId) ? (
                  <Button
                    variant="tonal"
                    size="small"
                    onClick={() => openGroupChat(group.id, group.groupChatId)}
                    sx={{ alignSelf: 'flex-end', textTransform: 'none', borderRadius: 4 }}
                  >
                    Вы в группе · Открыть чат
                  </Button>
                ) : (
                  <Button
                    variant="filled"
                    size="small"
                    disabled={joiningId === group.id || group.currentSize >= group.maxSize}
                    onClick={() => handleJoin(group.id)}
                    sx={{ alignSelf: 'flex-end', textTransform: 'none', borderRadius: 4 }}
                  >
                    {group.currentSize >= group.maxSize ? 'Мест нет' : joiningId === group.id ? 'Вступаем...' : 'Вступить'}
                  </Button>
                )}
              </Card>
            ))}
          </Box>

          {joinError && (
            <Alert severity="error" sx={{ mb: 1 }}>
              {joinError}
            </Alert>
          )}
        </>
      )}

      {showModal && (
        <CreateGroupModal
          eventId={eventId}
          onClose={() => setShowModal(false)}
          onCreated={handleGroupCreated}
        />
      )}
    </Box>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────

export default function EventDetailScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  // Use liked state passed from EventCard/LikesScreen via navigation state
  const passedLiked = (location.state as { liked?: boolean } | null)?.liked ?? false;

  const [event, setEvent] = useState<EventDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [liked, setLiked] = useState(passedLiked);
  const [likeLoading, setLikeLoading] = useState(false);
  const [showUnlikeConfirm, setShowUnlikeConfirm] = useState(false);
  const [heartAnim, setHeartAnim] = useState(false);
  const [snack, setSnack] = useState('');

  useEffect(() => {
    async function fetchEvent() {
      setLoading(true);
      setError('');

      try {
        const response = await apiClient.get<EventDetail>(`/events/${id}`);
        setEvent(response.data);
        // Update liked state from server (authoritative source)
        setLiked(response.data.liked);
      } catch {
        setError('Не удалось загрузить событие');
      } finally {
        setLoading(false);
      }
    }

    fetchEvent();
  }, [id]);

  function handleLikeClick() {
    if (likeLoading || !event) {
      return;
    }

    // If unliking and there's a match, show confirmation dialog
    if (liked && event.hasMatch) {
      setShowUnlikeConfirm(true);
      return;
    }

    performLikeToggle();
  }

  async function performLikeToggle() {
    if (likeLoading || !event) {
      return;
    }

    setLikeLoading(true);
    const newLiked = !liked;

    try {
      if (newLiked) {
        await apiClient.post(`/events/${event.id}/like`);
      } else {
        await apiClient.delete(`/events/${event.id}/like`);
      }
      setLiked(newLiked);
      if (newLiked) {
        setHeartAnim(true);
        setTimeout(() => setHeartAnim(false), 400);
      }
      if (!newLiked) {
        setEvent((prev) => prev ? { ...prev, hasMatch: false } : prev);
      }
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setSnack(message || (newLiked ? 'Не удалось поставить лайк' : 'Не удалось убрать лайк'));
    } finally {
      setLikeLoading(false);
    }
  }

  function handleConfirmUnlike() {
    setShowUnlikeConfirm(false);
    performLikeToggle();
  }

  function handleBuyTicket() {
    if (!event?.ticketUrl) {
      return;
    }

    openExternalLink(event.ticketUrl);
  }

  function handleBack() {
    // Opened via a deep link / notification there is no in-app history to go back to.
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0;
    if (idx > 0) {
      navigate(-1);
    } else {
      navigate('/afisha', { replace: true });
    }
  }

  async function handleCopyPromo(code: string) {
    try {
      if (!(await nativeCopy(code))) await navigator.clipboard.writeText(code);
      setSnack(`Промокод ${code} скопирован`);
    } catch {
      setSnack(`Промокод: ${code}`);
    }
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', py: 6, gap: 2 }}>
          <CircularProgress />
          <Typography variant="body2" color="text.secondary">Загрузка...</Typography>
        </Box>
      </Box>
    );
  }

  if (error || !event) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', py: 6, px: 2, gap: 1.5 }}>
          <Alert severity="error" sx={{ width: '100%', maxWidth: 360 }}>
            {error || 'Событие не найдено'}
          </Alert>
          <Button variant="outlined" onClick={handleBack} sx={{ textTransform: 'none', borderRadius: 5 }}>
            Назад
          </Button>
        </Box>
      </Box>
    );
  }

  const typeLabel = INTEREST_LABELS[event.type as InterestType] ?? event.type;
  const formattedPrice = formatPrice(event.price);
  const hasStudentPromo = !!event.studentPromoCode
    && event.originalPrice != null
    && event.price != null
    && Number(event.originalPrice) > Number(event.price);
  const formattedOriginalPrice = event.originalPrice != null
    ? formatPrice(event.originalPrice)
    : null;
  const whenWhere = [formatEventDateTime(event.eventDate, event.eventTime), event.city]
    .filter(Boolean)
    .join(' · ');

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100%' }}>
      <Box sx={{ position: 'relative', width: '100%', aspectRatio: '16 / 9', overflow: 'hidden', bgcolor: 'surfaceVariant.main' }}>
      <IconButton
        onClick={handleBack}
        aria-label="Назад"
        sx={{
          position: 'absolute',
          top: 12,
          left: 12,
          zIndex: 1,
          bgcolor: 'rgba(18, 18, 18, 0.56)',
          color: '#fff',
          backdropFilter: 'blur(12px)',
          '&:hover': { bgcolor: 'rgba(18, 18, 18, 0.72)' },
        }}
      >
        <ArrowBackOutlined />
      </IconButton>
        {event.imageUrl ? (
          <Box
            component="img"
            src={event.imageUrl}
            alt={event.title}
            sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block', animation: 'fadeIn 0.5s ease-out' }}
          />
        ) : (
          <Box sx={{ width: '100%', height: '100%', bgcolor: 'surfaceVariant.main' }} />
        )}
      </Box>

      {/* Content */}
      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5, animation: 'fadeInUp 0.4s ease-out' }}>
        <Typography variant="h5" fontWeight={700} sx={{ lineHeight: 1.3, overflowWrap: 'anywhere' }}>
          {event.title}
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Chip
            label={typeLabel}
            size="small"
            sx={getTagChipSx(typeLabel)}
          />
          <Typography variant="body2" color="text.secondary">
            {whenWhere}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Chip
            label={formattedPrice || 'Цена — на сайте продавца'}
            size="small"
            variant={hasStudentPromo ? 'filled' : 'outlined'}
            sx={(theme) => ({
              alignSelf: 'flex-start',
              fontWeight: 700,
              ...(hasStudentPromo && {
                bgcolor: theme.palette.error.main,
                color: theme.palette.common.white,
              }),
            })}
          />
          {hasStudentPromo && formattedOriginalPrice && (
            <Typography
              variant="body2"
              sx={{ color: 'onSurfaceVariant.main', textDecoration: 'line-through', fontWeight: 500 }}
            >
              {formattedOriginalPrice}
            </Typography>
          )}
          {hasStudentPromo && event.studentPromoCode && (
            <Chip
              label={`Промокод: ${event.studentPromoCode}`}
              size="small"
              icon={<ContentCopyOutlined sx={{ fontSize: 14 }} />}
              onClick={() => handleCopyPromo(event.studentPromoCode!)}
              sx={(theme) => ({
                maxWidth: '100%',
                '& .MuiChip-icon': { color: 'inherit' },
                bgcolor: theme.palette.error.main,
                color: theme.palette.common.white,
                fontWeight: 600,
              })}
            />
          )}
        </Box>
        {hasStudentPromo && event.studentPromoNote && (
          <Box
            sx={(theme) => ({
              bgcolor: theme.palette.errorContainer?.main ?? theme.palette.error.light,
              color: theme.palette.onErrorContainer?.main ?? theme.palette.common.white,
              borderRadius: 2,
              p: 1.25,
              fontSize: '0.85rem',
              lineHeight: 1.5,
            })}
          >
            {event.studentPromoNote}
          </Box>
        )}

        {event.description && (
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6, whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>
            <LinkifiedText text={event.description} />
          </Typography>
        )}

        {event.pushkinCard && <PushkinCardInfo variant="event" />}

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 1, pb: 3 }}>
          {event.ticketUrl && (
            <Button
              variant="filled"
              onClick={handleBuyTicket}
              sx={{ flex: 1, textTransform: 'none', borderRadius: 6, py: 1.2, fontWeight: 600 }}
            >
              Купить билет
            </Button>
          )}
          <IconButton
            onClick={handleLikeClick}
            aria-label={liked ? 'Убрать из избранного' : 'В избранное'}
            disabled={likeLoading}
            sx={{
              width: 48,
              height: 48,
              border: 2,
              borderStyle: 'solid',
              borderColor: liked ? 'error.main' : 'divider',
              color: liked ? 'error.main' : 'text.secondary',
              flexShrink: 0,
              transition: 'transform 0.15s, border-color 0.2s',
              '&:hover': { transform: 'scale(1.1)' },
            }}
          >
            {liked ? (
              <FavoriteOutlined sx={heartAnim ? { animation: 'heartPop 0.4s ease-out' } : undefined} />
            ) : (
              <FavoriteBorderOutlined />
            )}
          </IconButton>
        </Box>

        {/* Groups section */}
        <GroupsSection eventId={event.id} />
      </Box>

      <Dialog open={showUnlikeConfirm} onClose={() => setShowUnlikeConfirm(false)} maxWidth="xs">
        <DialogTitle>Убрать лайк?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            У вас есть метч на это мероприятие. Если вы уберёте лайк, чат метча будет удалён.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowUnlikeConfirm(false)} sx={{ textTransform: 'none' }}>Отмена</Button>
          <Button onClick={handleConfirmUnlike} color="error" sx={{ textTransform: 'none' }}>Убрать лайк</Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={!!snack}
        autoHideDuration={3000}
        onClose={() => setSnack('')}
        message={snack}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        sx={{ bottom: { xs: 90 } }}
      />
    </Box>
  );
}
