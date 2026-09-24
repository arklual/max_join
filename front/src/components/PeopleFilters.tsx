import { useEffect, useState, useCallback } from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import apiClient from '../api/client';
import type { SearchCriteria } from '../types';
import UniversitySelect from './UniversitySelect';

interface PeopleFiltersProps {
  city?: string;
}

/** Parses an age input; empty/invalid → null. */
function parseAge(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const n = Number(trimmed);
  return Number.isInteger(n) ? n : NaN;
}

interface UniversityLite {
  id: number;
  name: string;
}

export default function PeopleFilters({ city }: PeopleFiltersProps) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [savedToast, setSavedToast] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);

  const [gender, setGender] = useState<'MALE' | 'FEMALE' | null>(null);
  const [ageMin, setAgeMin] = useState<string>('');
  const [ageMax, setAgeMax] = useState<string>('');
  const [universityId, setUniversityId] = useState<number | null>(null);
  const [universityName, setUniversityName] = useState<string>('');

  const loadCriteria = useCallback(async () => {
    setLoading(true);
    setError('');
    setLoadFailed(false);
    try {
      const response = await apiClient.get<SearchCriteria>('/users/me/search-criteria');
      const data = response.data;
      setGender(data.preferredGender);
      setAgeMin(data.preferredAgeMin != null ? String(data.preferredAgeMin) : '');
      setAgeMax(data.preferredAgeMax != null ? String(data.preferredAgeMax) : '');
      setUniversityId(data.preferredUniversityId ?? null);

      if (data.preferredUniversityId) {
        try {
          const uniResp = await apiClient.get<UniversityLite>(`/universities/${data.preferredUniversityId}`);
          setUniversityName(uniResp.data.name);
        } catch {
          setUniversityName('');
        }
      } else {
        setUniversityName('');
      }
    } catch {
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCriteria();
  }, [loadCriteria]);

  async function handleSave() {
    if (loadFailed) return;
    const min = parseAge(ageMin);
    const max = parseAge(ageMax);
    if (
      Number.isNaN(min) ||
      Number.isNaN(max) ||
      (min !== null && (min < 1 || min > 150)) ||
      (max !== null && (max < 1 || max > 150)) ||
      (min !== null && max !== null && min > max)
    ) {
      setError('Проверьте диапазон возраста.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await apiClient.put('/users/me/search-criteria', {
        preferredGender: gender,
        preferredAgeMin: min,
        preferredAgeMax: max,
        preferredUniversityId: universityId,
      });
      setSavedToast('Сохранено');
      setTimeout(() => setSavedToast(''), 1500);
    } catch {
      setError('Не удалось сохранить настройки.');
    } finally {
      setSaving(false);
    }
  }

  function handleReset() {
    setGender(null);
    setAgeMin('');
    setAgeMax('');
    setError('');
    setUniversityId(null);
    setUniversityName('');
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  if (loadFailed) {
    return (
      <Box
        sx={{
          bgcolor: 'surfaceContainerLow.main',
          borderRadius: 3,
          p: 1.5,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 1,
          border: 1,
          borderColor: 'outlineVariant.main',
          textAlign: 'center',
        }}
      >
        <Typography variant="body2" color="error">
          Не удалось загрузить настройки поиска людей.
        </Typography>
        <Button
          variant="outlined"
          onClick={loadCriteria}
          sx={{ borderRadius: 3, textTransform: 'none' }}
        >
          Повторить
        </Button>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        bgcolor: 'surfaceContainerLow.main',
        borderRadius: 3,
        p: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        border: 1,
        borderColor: 'outlineVariant.main',
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 600, color: 'onSurface.main' }}>
        Фильтр людей для метча
      </Typography>
      <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', mt: -1 }}>
        Применяется ко всем мероприятиям. Метч произойдёт только с людьми,
        подходящими под эти критерии.
      </Typography>

      {error && <Alert severity="error">{error}</Alert>}

      {/* Gender */}
      <Box>
        <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', mb: 0.5, display: 'block' }}>
          Пол
        </Typography>
        <ToggleButtonGroup
          value={gender}
          exclusive
          onChange={(_e, val: 'MALE' | 'FEMALE' | null) => setGender(val)}
          fullWidth
          size="small"
          sx={{
            gap: 1,
            '& .MuiToggleButton-root': {
              textTransform: 'none',
              fontWeight: 600,
              borderRadius: '10px !important',
              border: '1px solid',
              borderColor: 'outlineVariant.main',
            },
          }}
        >
          <ToggleButton value="MALE">Мужской</ToggleButton>
          <ToggleButton value="FEMALE">Женский</ToggleButton>
        </ToggleButtonGroup>
        <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', mt: 0.5, display: 'block' }}>
          {gender === null ? 'Любой пол' : 'Нажмите ещё раз, чтобы сбросить'}
        </Typography>
      </Box>

      {/* Age */}
      <Box>
        <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', mb: 0.5, display: 'block' }}>
          Возраст
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ pt: 0.75 }}>
          <TextField
            label="От"
            type="text"
            inputProps={{ inputMode: 'numeric', pattern: '[0-9]*', maxLength: 3 }}
            placeholder="18"
            value={ageMin}
            onChange={(e) => setAgeMin(e.target.value.replace(/\D/g, '').slice(0, 3))}
            size="small"
            sx={{ flex: 1 }}
          />
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>—</Typography>
          <TextField
            label="До"
            type="text"
            inputProps={{ inputMode: 'numeric', pattern: '[0-9]*', maxLength: 3 }}
            placeholder="99"
            value={ageMax}
            onChange={(e) => setAgeMax(e.target.value.replace(/\D/g, '').slice(0, 3))}
            size="small"
            sx={{ flex: 1 }}
          />
        </Stack>
      </Box>

      {/* University */}
      <Box>
        <Typography variant="caption" sx={{ color: 'onSurfaceVariant.main', mb: 0.5, display: 'block' }}>
          ВУЗ
        </Typography>
        <UniversitySelect
          id="people-filter-uni"
          label=""
          value={universityName}
          universityId={universityId}
          onChange={(name, id) => {
            setUniversityName(name);
            setUniversityId(id);
          }}
          city={city}
        />
      </Box>

      <Stack direction="row" spacing={1}>
        <Button
          variant="outlined"
          onClick={handleReset}
          sx={{ flex: 1, borderRadius: 3, textTransform: 'none' }}
        >
          Сбросить
        </Button>
        <Button
          variant="filled"
          onClick={handleSave}
          disabled={saving}
          sx={{ flex: 1, borderRadius: 3, textTransform: 'none' }}
        >
          {saving ? 'Сохранение...' : savedToast || 'Сохранить'}
        </Button>
      </Stack>
    </Box>
  );
}
