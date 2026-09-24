import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { INTEREST_LABELS, type InterestType } from '../types';
import type { EventCard as EventCardType } from '../types';
import apiClient from '../api/client';
import Card from '@mui/material/Card';
import CardMedia from '@mui/material/CardMedia';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Snackbar from '@mui/material/Snackbar';
import Portal from '@mui/material/Portal';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import { getTagChipSx } from './tagChipStyles';
import { formatEventDateTime } from '../utils/dateUtils';
import { formatPrice } from '../utils/format';
import { PushkinCardChip } from './PushkinCardInfo';

interface EventCardProps {
  event: EventCardType;
  onLikeToggle?: (eventId: number, liked: boolean) => void;
}

export default function EventCard({ event, onLikeToggle }: EventCardProps) {
  const navigate = useNavigate();
  const [liked, setLiked] = useState(event.liked);
  const [likeLoading, setLikeLoading] = useState(false);
  const [showUnlikeConfirm, setShowUnlikeConfirm] = useState(false);
  const [heartAnim, setHeartAnim] = useState(false);
  const [likeError, setLikeError] = useState('');
  const prevLikedRef = useRef(liked);

  useEffect(() => {
    if (liked && !prevLikedRef.current) {
      setHeartAnim(true);
      const timer = setTimeout(() => setHeartAnim(false), 400);
      return () => clearTimeout(timer);
    }
    prevLikedRef.current = liked;
  }, [liked]);

  function handleCardClick() {
    navigate(`/events/${event.id}`, { state: { liked } });
  }

  function handleLikeClick(e: React.MouseEvent) {
    e.stopPropagation();

    if (likeLoading) {
      return;
    }

    if (liked && event.hasMatch) {
      setShowUnlikeConfirm(true);
      return;
    }

    performLikeToggle();
  }

  async function performLikeToggle() {
    setLikeLoading(true);
    const newLiked = !liked;

    try {
      if (newLiked) {
        await apiClient.post(`/events/${event.id}/like`);
      } else {
        await apiClient.delete(`/events/${event.id}/like`);
      }
      setLiked(newLiked);
      onLikeToggle?.(event.id, newLiked);
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setLikeError(message || (newLiked ? 'Не удалось поставить лайк' : 'Не удалось убрать лайк'));
    } finally {
      setLikeLoading(false);
    }
  }

  function handleConfirmUnlike() {
    setShowUnlikeConfirm(false);
    performLikeToggle();
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

  return (
    <Card
      variant="outlined"
      onClick={handleCardClick}
      role="button"
      tabIndex={0}
      sx={(theme) => ({
        cursor: 'pointer',
        borderRadius: 4,
        bgcolor: theme.palette.surfaceContainerLow.main,
        borderColor: theme.palette.outlineVariant.main,
        overflow: 'hidden',
        transition: 'transform 0.15s, box-shadow 0.15s',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: theme.shadows[4],
        },
      })}
    >
      <Box sx={{ position: 'relative', width: '100%', aspectRatio: '4 / 3' }}>
        {event.imageUrl ? (
          <CardMedia
            component="img"
            image={event.imageUrl}
            alt={event.title}
            loading="lazy"
            sx={{ width: '100%', height: '100%', objectFit: 'cover', animation: 'fadeIn 0.4s ease-out' }}
          />
        ) : (
          <Box
            sx={(theme) => ({
              width: '100%',
              height: '100%',
              bgcolor: theme.palette.surfaceVariant.main,
            })}
          />
        )}

        <Chip
          label={typeLabel}
          size="small"
          sx={{
            position: 'absolute',
            top: 8,
            left: 8,
            maxWidth: hasStudentPromo ? 'calc(50% - 12px)' : 'calc(100% - 16px)',
            fontSize: '0.7rem',
            height: 24,
            ...getTagChipSx(typeLabel),
          }}
        />

        <Box
          sx={{
            position: 'absolute',
            bottom: 8,
            left: 8,
            right: 48,
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 0.5,
          }}
        >
          {formattedPrice && (
          <Chip
            label={formattedPrice}
            size="small"
            sx={(theme) => ({
              bgcolor: hasStudentPromo ? theme.palette.error.main : theme.palette.inverseSurface.main,
              color: hasStudentPromo ? theme.palette.common.white : theme.palette.inverseOnSurface.main,
              fontWeight: 700,
              fontSize: '0.75rem',
              height: 24,
            })}
          />
          )}
          {hasStudentPromo && formattedOriginalPrice && (
            <Chip
              label={formattedOriginalPrice}
              size="small"
              sx={(theme) => ({
                bgcolor: theme.palette.surface.main,
                color: theme.palette.onSurfaceVariant.main,
                fontWeight: 500,
                fontSize: '0.7rem',
                height: 22,
                textDecoration: 'line-through',
                '& .MuiChip-label': { textDecoration: 'line-through' },
              })}
            />
          )}
        </Box>
        {hasStudentPromo && event.studentPromoCode && (
          <Chip
            label={`Студентам: ${event.studentPromoCode}`}
            size="small"
            sx={(theme) => ({
              position: 'absolute',
              top: 8,
              right: 8,
              maxWidth: 'calc(50% - 12px)',
              bgcolor: theme.palette.error.main,
              color: theme.palette.common.white,
              fontWeight: 600,
              fontSize: '0.65rem',
              height: 22,
            })}
          />
        )}

        <IconButton
          onClick={handleLikeClick}
          aria-label={liked ? 'Убрать из избранного' : 'В избранное'}
          disabled={likeLoading}
          size="small"
          sx={(theme) => ({
            position: 'absolute',
            bottom: 8,
            right: 8,
            bgcolor: theme.palette.surface.main,
            color: liked ? theme.palette.error.main : theme.palette.onSurfaceVariant.main,
            '&:hover': {
              bgcolor: theme.palette.surfaceContainerHigh.main,
            },
            '&.Mui-disabled': {
              opacity: 0.5,
            },
          })}
        >
          {liked ? (
            <FavoriteIcon
              fontSize="small"
              sx={heartAnim ? { animation: 'heartPop 0.4s ease-out' } : undefined}
            />
          ) : (
            <FavoriteBorderIcon fontSize="small" />
          )}
        </IconButton>
      </Box>

      <CardContent sx={{ py: 1, px: 1.25, '&:last-child': { pb: 1.25 } }}>
        <Typography
          variant="body2"
          title={event.title}
          sx={(theme) => ({
            fontSize: '0.8rem',
            fontWeight: 600,
            color: theme.palette.onSurface.main,
            lineHeight: 1.3,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
            minHeight: '2.6em',
          })}
        >
          {event.title}
        </Typography>
        {event.eventDate && (
          <Typography
            variant="caption"
            noWrap
            component="div"
            sx={(theme) => ({ color: theme.palette.onSurfaceVariant.main, mt: 0.5, fontSize: '0.72rem' })}
          >
            {formatEventDateTime(event.eventDate, event.eventTime, 'short')}
          </Typography>
        )}
        {event.pushkinCard && (
          <Box sx={{ mt: 0.75 }}>
            <PushkinCardChip />
          </Box>
        )}
      </CardContent>

      <Dialog
        open={showUnlikeConfirm}
        onClose={() => setShowUnlikeConfirm(false)}
        onClick={(e) => e.stopPropagation()}
        maxWidth="xs"
      >
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

      {/* Portal: the card may carry a CSS transform, which would re-anchor a fixed Snackbar to it. */}
      <Portal>
      <Snackbar
        open={!!likeError}
        autoHideDuration={3000}
        onClose={() => setLikeError('')}
        onClick={(e) => e.stopPropagation()}
        message={likeError}
        sx={{ bottom: { xs: 90 } }}
      />
      </Portal>
    </Card>
  );
}
