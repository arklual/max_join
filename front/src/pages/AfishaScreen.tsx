import { useState, useEffect, useCallback, useRef } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Collapse from '@mui/material/Collapse';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import CreditCardOutlined from '@mui/icons-material/CreditCardOutlined';
import FilterListOutlined from '@mui/icons-material/FilterListOutlined';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import apiClient from '../api/client';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import type { EventCard as EventCardType, EventFilters as EventFiltersType, PageResponse, UserProfile } from '../types';
import SearchBar from '../components/SearchBar';
import EventFiltersPanel from '../components/EventFilters';
import EventCard from '../components/EventCard';
import PeopleFilters from '../components/PeopleFilters';
import HowItWorksCard from '../components/HowItWorksCard';
import { rememberProfileAge, usePushkinEligible } from '../utils/pushkin';

const PAGE_SIZE = 20;
const INPUT_DEBOUNCE_MS = 350;

const SESSION_KEY_SEARCH = 'afisha_search';
const SESSION_KEY_FILTERS = 'afisha_filters';
const SESSION_KEY_FILTERS_OPEN = 'afisha_filters_open';
const SESSION_KEY_PEOPLE_OPEN = 'afisha_people_filters_open';

function getStoredSearch(): string {
  return sessionStorage.getItem(SESSION_KEY_SEARCH) ?? '';
}

function getStoredFilters(): EventFiltersType {
  try {
    const stored = sessionStorage.getItem(SESSION_KEY_FILTERS);
    return stored ? (JSON.parse(stored) as EventFiltersType) : {};
  } catch {
    return {};
  }
}

function getStoredFiltersOpen(): boolean {
  return sessionStorage.getItem(SESSION_KEY_FILTERS_OPEN) === 'true';
}

function getStoredPeopleOpen(): boolean {
  return sessionStorage.getItem(SESSION_KEY_PEOPLE_OPEN) === 'true';
}

export default function AfishaScreen() {
  const [events, setEvents] = useState<EventCardType[]>([]);
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(false);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState('');
  const [loadMoreError, setLoadMoreError] = useState('');

  // `search` / `filters` reflect the inputs immediately; `query*` are the
  // (debounced) values actually used for requests.
  const [search, setSearch] = useState<string>(getStoredSearch);
  const [filters, setFilters] = useState<EventFiltersType>(getStoredFilters);
  const [querySearch, setQuerySearch] = useState<string>(getStoredSearch);
  const [queryFilters, setQueryFilters] = useState<EventFiltersType>(getStoredFilters);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestIdRef = useRef(0);
  const [filtersOpen, setFiltersOpen] = useState<boolean>(getStoredFiltersOpen);
  const [peopleOpen, setPeopleOpen] = useState<boolean>(getStoredPeopleOpen);
  const [profileCity, setProfileCity] = useState<string | undefined>(undefined);
  const [pushkinEvents, setPushkinEvents] = useState<EventCardType[]>([]);

  const [recommended, setRecommended] = useState<EventCardType[]>([]);
  const [recommendedLoading, setRecommendedLoading] = useState(false);

  // Persist state to sessionStorage so it survives navigation
  useEffect(() => {
    sessionStorage.setItem(SESSION_KEY_SEARCH, search);
  }, [search]);

  useEffect(() => {
    sessionStorage.setItem(SESSION_KEY_FILTERS, JSON.stringify(filters));
  }, [filters]);

  useEffect(() => {
    sessionStorage.setItem(SESSION_KEY_FILTERS_OPEN, String(filtersOpen));
  }, [filtersOpen]);

  useEffect(() => {
    sessionStorage.setItem(SESSION_KEY_PEOPLE_OPEN, String(peopleOpen));
  }, [peopleOpen]);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<UserProfile>('/users/me/profile')
      .then((res) => {
        if (!cancelled) {
          setProfileCity(res.data.city);
          rememberProfileAge(res.data.age);
        }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const hasNoFilters =
    !querySearch.trim() &&
    queryFilters.minPrice === undefined &&
    queryFilters.maxPrice === undefined &&
    queryFilters.dateFrom === undefined &&
    queryFilters.dateTo === undefined &&
    !queryFilters.pushkinCard &&
    (!queryFilters.tagIds || queryFilters.tagIds.length === 0);

  // The Pushkin card is for ages 14–22: they get a dedicated shelf and filter, others see nothing about it.
  const pushkinEligibility = usePushkinEligible();
  const pushkinEligible = pushkinEligibility === true;

  // A stored filter or a `pushkin` deep link must not narrow the afisha for users the card doesn't apply to.
  useEffect(() => {
    if (pushkinEligibility === false && filters.pushkinCard) {
      handleFiltersChange({ ...filters, pushkinCard: undefined });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pushkinEligibility, filters.pushkinCard]);

  useEffect(() => {
    if (!pushkinEligible || !hasNoFilters) {
      setPushkinEvents([]);
      return;
    }
    let cancelled = false;
    apiClient
      .get<PageResponse<EventCardType>>('/events', { params: { pushkinCard: true, page: 0, size: 10 } })
      .then((res) => {
        if (!cancelled) setPushkinEvents(res.data.content);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [pushkinEligible, hasNoFilters]);

  function showAllPushkin() {
    handleFiltersChange({ ...filters, pushkinCard: true });
  }

  useEffect(() => {
    if (!hasNoFilters) {
      setRecommended([]);
      return;
    }
    let cancelled = false;
    setRecommendedLoading(true);
    apiClient
      .get<EventCardType[]>('/events/recommended', { params: { limit: 6 } })
      .then((res) => {
        if (!cancelled) setRecommended(res.data);
      })
      .catch(() => {
        if (!cancelled) setRecommended([]);
      })
      .finally(() => {
        if (!cancelled) setRecommendedLoading(false);
      });
    return () => { cancelled = true; };
  }, [hasNoFilters]);

  function handleRecommendedLikeToggle(eventId: number, liked: boolean) {
    setRecommended((prev) =>
      prev.map((ev) => (ev.id === eventId ? { ...ev, liked } : ev))
    );
    // Also update in main list if present
    setEvents((prev) =>
      prev.map((ev) => (ev.id === eventId ? { ...ev, liked } : ev))
    );
  }

  const fetchEvents = useCallback(async (pageNum: number, isLoadMore: boolean) => {
    const requestId = ++requestIdRef.current;
    const search = querySearch;
    const filters = queryFilters;
    setLoading(true);
    if (isLoadMore) {
      setLoadMoreError('');
    } else {
      setError('');
      setLoadMoreError('');
    }

    try {
      const params: Record<string, string | number> = {
        page: pageNum,
        size: PAGE_SIZE,
      };

      if (search.trim()) {
        params.search = search.trim();
      }
      if (filters.minPrice !== undefined) {
        params.minPrice = filters.minPrice;
      }
      if (filters.maxPrice !== undefined) {
        params.maxPrice = filters.maxPrice;
      }
      if (filters.dateFrom) {
        params.dateFrom = filters.dateFrom;
      }
      if (filters.dateTo) {
        params.dateTo = filters.dateTo;
      }

      let url = '/events';
      const searchParams = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        searchParams.append(key, String(value));
      }
      if (filters.tagIds && filters.tagIds.length > 0) {
        searchParams.append('tagIds', filters.tagIds.join(','));
      }
      if (filters.pushkinCard) {
        searchParams.append('pushkinCard', 'true');
      }
      url += '?' + searchParams.toString();

      const response = await apiClient.get<PageResponse<EventCardType>>(url);
      // Ignore stale responses (a newer request was started meanwhile)
      if (requestId !== requestIdRef.current) return;
      const data = response.data;

      if (isLoadMore) {
        setEvents((prev) => [...prev, ...data.content]);
      } else {
        setEvents(data.content);
      }
      setLastPage(data.last);
      setPage(data.number);
    } catch {
      if (requestId !== requestIdRef.current) return;
      if (isLoadMore) {
        setLoadMoreError('Не удалось загрузить ещё события');
      } else {
        setError('Не удалось загрузить события');
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
        setInitialLoading(false);
      }
    }
  }, [querySearch, queryFilters]);

  useEffect(() => {
    fetchEvents(0, false);
  }, [fetchEvents]);

  function handleLoadMore() {
    if (!loading && !lastPage) {
      fetchEvents(page + 1, true);
    }
  }

  // Next page loads automatically as the end of the list approaches.
  const sentinelRef = useInfiniteScroll<HTMLDivElement>(handleLoadMore, {
    enabled: events.length > 0 && !lastPage && !loadMoreError,
    loading,
    itemCount: events.length,
  });

  function handleLikeToggle(eventId: number, liked: boolean) {
    setEvents((prev) =>
      prev.map((ev) => (ev.id === eventId ? { ...ev, liked } : ev))
    );
    setRecommended((prev) =>
      prev.map((ev) => (ev.id === eventId ? { ...ev, liked } : ev))
    );
  }

  function scheduleQuery(nextSearch: string, nextFilters: EventFiltersType, immediate: boolean) {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
    if (immediate) {
      setQuerySearch(nextSearch);
      setQueryFilters(nextFilters);
      return;
    }
    debounceRef.current = setTimeout(() => {
      debounceRef.current = null;
      setQuerySearch(nextSearch);
      setQueryFilters(nextFilters);
    }, INPUT_DEBOUNCE_MS);
  }

  function handleSearchChange(value: string) {
    setSearch(value);
    // Clearing the search via the clear button applies immediately
    scheduleQuery(value, filters, value === '');
  }

  function handleFiltersChange(newFilters: EventFiltersType) {
    setFilters(newFilters);
    // Price fields are typed — debounce them; dates and tags apply immediately
    const priceChanged =
      newFilters.minPrice !== filters.minPrice || newFilters.maxPrice !== filters.maxPrice;
    scheduleQuery(search, newFilters, !priceChanged);
  }

  function handleResetFilters() {
    setSearch('');
    setFilters({});
    scheduleQuery('', {}, true);
    sessionStorage.removeItem(SESSION_KEY_SEARCH);
    sessionStorage.removeItem(SESSION_KEY_FILTERS);
  }

  const hasActiveFilters =
    filters.minPrice !== undefined ||
    filters.maxPrice !== undefined ||
    filters.dateFrom !== undefined ||
    filters.dateTo !== undefined ||
    !!filters.pushkinCard ||
    (filters.tagIds && filters.tagIds.length > 0);

  const hasActiveSearchOrFilters = hasActiveFilters || search.trim() !== '';

  // Max height for an expanded filter panel inside the sticky header, so that
  // its bottom buttons remain reachable on short screens (the header, search
  // bar, toggle buttons and bottom navigation take ~230px).
  const panelSx = {
    maxHeight: 'max(200px, calc(100dvh - 230px))',
    overflowY: 'auto',
    overscrollBehavior: 'contain',
    borderRadius: 3,
  } as const;

  return (
    <Box sx={{ p: 1.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 1,
          position: 'sticky',
          top: 0,
          zIndex: 10,
          bgcolor: 'surface.main',
          pb: 0.5,
        }}
      >
        <SearchBar value={search} onChange={handleSearchChange} placeholder="Поиск событий..." />
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Button
            variant="tonal"
            startIcon={<FilterListOutlined />}
            onClick={() => {
              setFiltersOpen((prev) => !prev);
              if (!filtersOpen) setPeopleOpen(false);
            }}
            sx={{
              borderRadius: 5,
              textTransform: 'none',
              ...(hasActiveFilters && {
                '&::after': {
                  content: '""',
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  bgcolor: 'primary.main',
                  ml: 0.75,
                },
              }),
            }}
          >
            {filtersOpen ? 'Скрыть фильтры' : 'Фильтры'}
          </Button>
          <Button
            variant="tonal"
            startIcon={<PeopleAltOutlined />}
            onClick={() => {
              setPeopleOpen((prev) => !prev);
              if (!peopleOpen) setFiltersOpen(false);
            }}
            sx={{
              borderRadius: 5,
              textTransform: 'none',
            }}
          >
            {peopleOpen ? 'Скрыть фильтр людей' : 'Кого ищу'}
          </Button>
        </Stack>
        <Collapse in={filtersOpen} unmountOnExit>
          <Box sx={panelSx}>
            <EventFiltersPanel
              filters={filters}
              onChange={handleFiltersChange}
              onReset={handleResetFilters}
              showPushkin={pushkinEligible}
            />
          </Box>
        </Collapse>
        <Collapse in={peopleOpen} unmountOnExit>
          <Box sx={panelSx}>
            <PeopleFilters city={profileCity} />
          </Box>
        </Collapse>
      </Box>

      {!initialLoading && <HowItWorksCard />}

      {filters.pushkinCard && pushkinEligible && (
        <Box>
          <Chip
            icon={<CreditCardOutlined />}
            label="По Пушкинской карте"
            onDelete={() => handleFiltersChange({ ...filters, pushkinCard: undefined })}
            sx={{ fontWeight: 600, bgcolor: 'tertiaryContainer.main', color: 'onTertiaryContainer.main', '& .MuiChip-icon, & .MuiChip-deleteIcon': { color: 'inherit' } }}
          />
        </Box>
      )}

      {!initialLoading && pushkinEvents.length > 0 && hasNoFilters && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, animation: 'fadeInUp 0.4s ease-out' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle1" fontWeight={600}>
                По Пушкинской карте
              </Typography>
              <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main' }}>
                Билеты за счёт 5 000 ₽ от государства
              </Typography>
            </Box>
            <Button size="small" onClick={showAllPushkin} sx={{ textTransform: 'none', fontWeight: 600, flexShrink: 0 }}>
              Все
            </Button>
          </Box>
          <Stack
            direction="row"
            sx={{ overflowX: 'auto', gap: 1.5, pb: 1, '&::-webkit-scrollbar': { display: 'none' }, scrollbarWidth: 'none' }}
          >
            {pushkinEvents.map((event) => (
              <Box key={event.id} sx={{ minWidth: 160, maxWidth: 180, flexShrink: 0 }}>
                <EventCard event={event} />
              </Box>
            ))}
          </Stack>
        </Box>
      )}

      {!initialLoading && recommended.length > 0 && hasNoFilters && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, animation: 'fadeInUp 0.4s ease-out' }}>
          <Typography variant="subtitle1" fontWeight={600}>
            Рекомендации для вас
          </Typography>
          <Stack
            direction="row"
            sx={{
              overflowX: 'auto',
              gap: 1.5,
              pb: 1,
              '&::-webkit-scrollbar': { display: 'none' },
              scrollbarWidth: 'none',
            }}
          >
            {recommended.map((event) => (
              <Box key={event.id} sx={{ minWidth: 160, maxWidth: 180, flexShrink: 0 }}>
                <EventCard event={event} onLikeToggle={handleRecommendedLikeToggle} />
              </Box>
            ))}
          </Stack>
        </Box>
      )}

      {recommendedLoading && hasNoFilters && !initialLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress size={24} />
        </Box>
      )}

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
          <Button variant="outlined" onClick={() => fetchEvents(0, false)}>
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
            animation: 'fadeInUp 0.4s ease-out',
          }}
        >
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
            Событий не найдено
          </Typography>
          {hasActiveSearchOrFilters && (
            <Button
              variant="tonal"
              onClick={handleResetFilters}
              sx={{ mt: 1.5, borderRadius: 5, textTransform: 'none' }}
            >
              Сбросить фильтры
            </Button>
          )}
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

          <Box ref={sentinelRef} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, py: 2, minHeight: 56 }}>
            {loadMoreError ? (
              <>
                <Typography variant="caption" color="error" sx={{ textAlign: 'center' }}>
                  {loadMoreError}
                </Typography>
                <Button variant="outlined" onClick={handleLoadMore} disabled={loading} sx={{ borderRadius: 6, textTransform: 'none' }}>
                  Повторить
                </Button>
              </>
            ) : !lastPage ? (
              <CircularProgress size={28} aria-label="Загружаем ещё мероприятия" />
            ) : (
              <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main' }}>
                Это все мероприятия по вашим фильтрам
              </Typography>
            )}
          </Box>
        </>
      )}
    </Box>
  );
}
