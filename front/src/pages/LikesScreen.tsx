import { useState, useEffect, useCallback } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Snackbar from '@mui/material/Snackbar';
import { useNavigate } from 'react-router';
import Typography from '@mui/material/Typography';
import FavoriteBorderOutlined from '@mui/icons-material/FavoriteBorderOutlined';
import apiClient from '../api/client';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import type { EventCard as EventCardType, PageResponse } from '../types';
import EventCard from '../components/EventCard';
import OutingsSection from '../components/OutingsSection';

const PAGE_SIZE = 20;

export default function LikesScreen() {
  const navigate = useNavigate();
  const [removed, setRemoved] = useState<{ event: EventCardType; index: number } | null>(null);
  const [events, setEvents] = useState<EventCardType[]>([]);
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchLikedEvents = useCallback(async (pageNum: number, isLoadMore: boolean) => {
    setLoading(true);
    setError('');

    try {
      const response = await apiClient.get<PageResponse<EventCardType>>('/events/liked', {
        params: {
          page: pageNum,
          size: PAGE_SIZE,
        },
      });
      const data = response.data;

      if (isLoadMore) {
        setEvents((prev) => [...prev, ...data.content]);
      } else {
        setEvents(data.content);
      }
      setLastPage(data.last);
      setPage(data.number);
    } catch {
      setError('Не удалось загрузить избранное');
    } finally {
      setLoading(false);
      setInitialLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLikedEvents(0, false);
  }, [fetchLikedEvents]);

  function handleLoadMore() {
    if (!loading && !lastPage) {
      fetchLikedEvents(page + 1, true);
    }
  }

  const sentinelRef = useInfiniteScroll<HTMLDivElement>(handleLoadMore, {
    enabled: events.length > 0 && !lastPage && !error,
    loading,
    itemCount: events.length,
  });

  function handleLikeToggle(eventId: number, liked: boolean) {
    if (liked) return;
    // Un-liked from Избранное: drop the card, but let the user undo.
    setEvents((prev) => {
      const index = prev.findIndex((ev) => ev.id === eventId);
      if (index >= 0) setRemoved({ event: { ...prev[index], liked: true }, index });
      return prev.filter((ev) => ev.id !== eventId);
    });
  }

  async function handleUndo() {
    if (!removed) return;
    const { event, index } = removed;
    setRemoved(null);
    try {
      await apiClient.post(`/events/${event.id}/like`);
      setEvents((prev) => {
        const next = [...prev];
        next.splice(Math.min(index, next.length), 0, event);
        return next;
      });
    } catch {
      // Like limit or network error — the card stays removed.
    }
  }

  return (
    <Box sx={{ p: 1.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>
        Избранное
      </Typography>

      <OutingsSection />

      {initialLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress />
        </Box>
      )}

      {error && !initialLoading && (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            py: 6,
            px: 2,
            textAlign: 'center',
            gap: 1.5,
          }}
        >
          <Typography variant="body2" color="error">
            {error}
          </Typography>
          <Button variant="outlined" onClick={() => fetchLikedEvents(0, false)}>
            Повторить
          </Button>
        </Box>
      )}

      {!initialLoading && !error && events.length === 0 && (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            py: 6,
            px: 2,
            textAlign: 'center',
            gap: 1.5,
            animation: 'fadeInUp 0.4s ease-out',
          }}
        >
          <FavoriteBorderOutlined sx={{ fontSize: 48, color: 'text.disabled' }} />
          <Typography variant="body2" color="text.secondary">
            Вы ещё не лайкнули ни одного мероприятия
          </Typography>
          <Button
            variant="filled"
            onClick={() => navigate('/afisha')}
            sx={{ borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
          >
            Перейти в Афишу
          </Button>
        </Box>
      )}

      {events.length > 0 && (
        <>
          <Stack
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: 1.5,
              animation: 'fadeInUp 0.35s ease-out',
              '@media (max-width: 320px)': {
                gridTemplateColumns: '1fr',
              },
            }}
          >
            {events.map((event) => (
              <EventCard
                key={event.id}
                event={event}
                onLikeToggle={handleLikeToggle}
              />
            ))}
          </Stack>

          {!lastPage && (
            <Box ref={sentinelRef} sx={{ display: 'flex', justifyContent: 'center', py: 2, minHeight: 56 }}>
              <CircularProgress size={28} aria-label="Загружаем ещё" />
            </Box>
          )}
        </>
      )}

      <Snackbar
        open={!!removed}
        autoHideDuration={4000}
        onClose={() => setRemoved(null)}
        message="Убрано из избранного"
        action={<Button color="inherit" size="small" onClick={handleUndo}>Вернуть</Button>}
        sx={{ bottom: { xs: 90 } }}
      />
    </Box>
  );
}
