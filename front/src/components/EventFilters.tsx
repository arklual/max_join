import { type ChangeEvent, useState, useEffect, useRef, useCallback } from 'react';
import apiClient from '../api/client';
import type { EventFilters as EventFiltersType, Tag } from '../types';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import Autocomplete from '@mui/material/Autocomplete';
import Checkbox from '@mui/material/Checkbox';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import CancelIcon from '@mui/icons-material/Cancel';
import CreditCardOutlined from '@mui/icons-material/CreditCardOutlined';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import { getTagChipSx } from './tagChipStyles';

interface EventFiltersProps {
  filters: EventFiltersType;
  onChange: (filters: EventFiltersType) => void;
  onReset?: () => void;
  /** The Pushkin card switch — only for users of card age (14–22). */
  showPushkin?: boolean;
  /** Cities JOIN serves; a switch is shown when there is more than one. */
  cities?: string[];
  /** The user's city if JOIN serves it — what the afisha shows by default. */
  homeCity?: string;
}

const ALL_CITIES = 'all';

// Survives remounts (the panel is unmounted when collapsed), so selected tags
// that aren't among the popular ones keep their chips.
const tagCache = new Map<number, Tag>();

export default function EventFilters({
  filters, onChange, onReset, showPushkin = false, cities = [], homeCity,
}: EventFiltersProps) {
  const [popularTags, setPopularTags] = useState<Tag[]>([]);
  const [searchResults, setSearchResults] = useState<Tag[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Track all seen tags by id so we can resolve selected IDs to Tag objects
  const [knownTags, setKnownTags] = useState<Map<number, Tag>>(() => new Map(tagCache));

  const registerTags = useCallback((tags: Tag[]) => {
    if (tags.length === 0) return;
    tags.forEach((t) => tagCache.set(t.id, t));
    setKnownTags((prev) => {
      const next = new Map(prev);
      tags.forEach((t) => next.set(t.id, t));
      return next;
    });
  }, []);

  // Load popular tags once on mount
  useEffect(() => {
    apiClient
      .get<Tag[]>('/tags/popular')
      .then((res) => {
        setPopularTags(res.data);
        registerTags(res.data);
      })
      .catch(() => {/* silently ignore */});
  }, [registerTags]);

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  // Debounced search
  const doSearch = useCallback((query: string) => {
    if (!query.trim()) {
      setSearchResults([]);
      return;
    }
    setSearchLoading(true);
    apiClient
      .get<Tag[]>('/tags', { params: { query: query.trim().toLowerCase() } })
      .then((res) => {
        setSearchResults(res.data);
        registerTags(res.data);
      })
      .catch(() => setSearchResults([]))
      .finally(() => setSearchLoading(false));
  }, [registerTags]);

  function handleNumberChange(e: ChangeEvent<HTMLInputElement>) {
    const { name, value } = e.target;
    const numValue = value === '' ? undefined : Number(value);
    onChange({ ...filters, [name]: numValue });
  }

  function handleDateChange(e: ChangeEvent<HTMLInputElement>) {
    const { name, value } = e.target;
    onChange({ ...filters, [name]: value || undefined });
  }

  const selectedIds = filters.tagIds ?? [];

  // Resolve selected IDs -> Tag objects
  const selectedTags: Tag[] = selectedIds
    .map((id) => knownTags.get(id))
    .filter((t): t is Tag => t !== undefined);

  // Options: merge popular + search results, deduplicate, put selected first
  const allOptions = new Map<number, Tag>();
  selectedTags.forEach((t) => allOptions.set(t.id, t));
  const sourceOptions = inputValue.trim() ? searchResults : popularTags;
  sourceOptions.forEach((t) => allOptions.set(t.id, t));
  const options = Array.from(allOptions.values());

  const hasActiveFilters =
    filters.minPrice !== undefined ||
    filters.maxPrice !== undefined ||
    filters.dateFrom !== undefined ||
    filters.dateTo !== undefined ||
    !!filters.pushkinCard ||
    filters.city !== undefined ||
    selectedIds.length > 0;

  const shownCity = filters.city ?? homeCity ?? ALL_CITIES;

  function selectCity(city: string) {
    // The user's own city is the default, so it is stored as "no choice".
    const home = homeCity ?? ALL_CITIES;
    onChange({ ...filters, city: city === home ? undefined : city });
  }

  return (
    <Box
      sx={(theme) => ({
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        p: 1.5,
        bgcolor: theme.palette.surfaceContainerLow.main,
        borderRadius: 3,
        border: `1px solid ${theme.palette.outlineVariant.main}`,
      })}
    >
      {/* City */}
      {cities.length > 1 && (
        <Stack spacing={0.75}>
          <Typography variant="caption" sx={(theme) => ({ color: theme.palette.onSurfaceVariant.main, fontWeight: 600 })}>
            Город
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
            {[...cities, ALL_CITIES].map((city) => (
              <Chip
                key={city}
                label={city === ALL_CITIES ? 'Все города' : city}
                size="small"
                color={shownCity === city ? 'primary' : 'default'}
                variant={shownCity === city ? 'filled' : 'outlined'}
                onClick={() => selectCity(city)}
              />
            ))}
          </Box>
        </Stack>
      )}

      {/* Pushkin card */}
      {showPushkin && (
        <FormControlLabel
          control={
            <Switch
              checked={!!filters.pushkinCard}
              onChange={(e) => onChange({ ...filters, pushkinCard: e.target.checked || undefined })}
            />
          }
          label={
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
              <CreditCardOutlined fontSize="small" sx={{ color: 'tertiary.main' }} />
              <Typography variant="body2" sx={{ fontWeight: 600 }}>Только по Пушкинской карте</Typography>
            </Box>
          }
          sx={{ mx: 0, justifyContent: 'space-between' }}
          labelPlacement="start"
        />
      )}

      {/* Price */}
      <Stack spacing={0.75}>
        <Typography
          variant="caption"
          sx={(theme) => ({
            fontWeight: 600,
            color: theme.palette.onSurfaceVariant.main,
          })}
        >
          Цена
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <TextField
            variant="outlined"
            size="small"
            type="number"
            name="minPrice"
            placeholder="от"
            inputProps={{ min: 0, inputMode: 'numeric', 'aria-label': 'Цена от' }}
            value={filters.minPrice ?? ''}
            onChange={handleNumberChange}
            sx={{ flex: 1, minWidth: 0 }}
          />
          <Typography
            variant="body2"
            sx={(theme) => ({
              color: theme.palette.outline.main,
              flexShrink: 0,
            })}
          >
            &ndash;
          </Typography>
          <TextField
            variant="outlined"
            size="small"
            type="number"
            name="maxPrice"
            placeholder="до"
            inputProps={{ min: 0, inputMode: 'numeric', 'aria-label': 'Цена до' }}
            value={filters.maxPrice ?? ''}
            onChange={handleNumberChange}
            sx={{ flex: 1, minWidth: 0 }}
          />
        </Stack>
      </Stack>

      {/* Date */}
      <Stack spacing={0.75}>
        <Typography
          variant="caption"
          sx={(theme) => ({
            fontWeight: 600,
            color: theme.palette.onSurfaceVariant.main,
          })}
        >
          Дата
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pt: 0.75 }}>
          <TextField
            variant="outlined"
            size="small"
            type="date"
            name="dateFrom"
            label="С"
            value={filters.dateFrom ?? ''}
            onChange={handleDateChange}
            inputProps={{ max: filters.dateTo }}
            sx={{ flex: 1, minWidth: 0 }}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            variant="outlined"
            size="small"
            type="date"
            name="dateTo"
            label="По"
            value={filters.dateTo ?? ''}
            onChange={handleDateChange}
            inputProps={{ min: filters.dateFrom }}
            sx={{ flex: 1, minWidth: 0 }}
            InputLabelProps={{ shrink: true }}
          />
        </Stack>
      </Stack>

      {/* Tags */}
      <Stack spacing={0.75}>
        <Typography
          variant="caption"
          sx={(theme) => ({
            fontWeight: 600,
            color: theme.palette.onSurfaceVariant.main,
          })}
        >
          Теги событий
        </Typography>

        <Autocomplete
          multiple
          disableCloseOnSelect
          options={options}
          value={selectedTags}
          inputValue={inputValue}
          onInputChange={(_e, value, reason) => {
            if (reason === 'input') {
              setInputValue(value);
              if (debounceRef.current) clearTimeout(debounceRef.current);
              debounceRef.current = setTimeout(() => doSearch(value), 350);
            } else if (reason === 'clear') {
              setInputValue('');
              setSearchResults([]);
            }
          }}
          onChange={(_e, newValue) => {
            registerTags(newValue);
            const ids = newValue.map((t) => t.id);
            onChange({ ...filters, tagIds: ids.length > 0 ? ids : undefined });
          }}
          getOptionLabel={(option) =>
            option.usageCount !== undefined && option.usageCount > 0
              ? `${option.name} (${option.usageCount})`
              : option.name
          }
          isOptionEqualToValue={(option, value) => option.id === value.id}
          loading={searchLoading}
          loadingText="Поиск..."
          noOptionsText={inputValue.trim() ? 'Теги не найдены' : 'Начните вводить для поиска'}
          renderOption={(props, option, { selected }) => (
            <li {...props} key={option.id}>
              <Checkbox
                icon={<CheckBoxOutlineBlankIcon fontSize="small" />}
                checkedIcon={<CheckBoxIcon fontSize="small" />}
                checked={selected}
                sx={{ mr: 1 }}
              />
              {option.usageCount !== undefined && option.usageCount > 0
                ? `${option.name} (${option.usageCount})`
                : option.name}
            </li>
          )}
          renderTags={(value, getTagProps) =>
            value.map((tag, index) => {
              const { key, ...rest } = getTagProps({ index });
              return (
                <Chip
                  key={key}
                  label={tag.name}
                  size="small"
                  deleteIcon={<CancelIcon />}
                  sx={{
                    fontSize: '0.8rem',
                    ...getTagChipSx(tag.name, { clickable: true, withDelete: true }),
                  }}
                  {...rest}
                />
              );
            })
          }
          renderInput={(params) => (
            <TextField
              {...params}
              variant="outlined"
              size="small"
              placeholder={selectedTags.length === 0 ? 'Выберите теги...' : ''}
              InputProps={{
                ...params.InputProps,
                endAdornment: (
                  <>
                    {searchLoading ? <CircularProgress size={16} /> : null}
                    {params.InputProps.endAdornment}
                  </>
                ),
              }}
            />
          )}
          size="small"
        />
      </Stack>

      {/* Reset button */}
      {onReset && (
        <Box sx={{ pt: 0.5 }}>
          <Button
            variant="tonal"
            onClick={onReset}
            fullWidth
            sx={(theme) => ({
              bgcolor: hasActiveFilters
                ? theme.palette.errorContainer.main
                : theme.palette.surfaceVariant.main,
              color: hasActiveFilters
                ? theme.palette.onErrorContainer.main
                : theme.palette.onSurfaceVariant.main,
              '&:hover': {
                bgcolor: hasActiveFilters
                  ? theme.palette.error.main
                  : theme.palette.surfaceContainerHighest.main,
                color: hasActiveFilters
                  ? theme.palette.onError.main
                  : theme.palette.onSurfaceVariant.main,
              },
            })}
          >
            Сбросить фильтры
          </Button>
        </Box>
      )}
    </Box>
  );
}
