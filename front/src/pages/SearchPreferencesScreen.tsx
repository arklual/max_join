import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Button,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Alert,
  Snackbar,
  CircularProgress,
  IconButton,
} from '@mui/material';
import { ArrowBack } from '@mui/icons-material';
import { useNavigate } from 'react-router';
import apiClient from '../api/client';
import type { SearchCriteria } from '../types';

/** Parses an age input; empty → null, invalid → NaN. */
function parseAge(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const n = Number(trimmed);
  return Number.isInteger(n) ? n : NaN;
}

export default function SearchPreferencesScreen() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);

  const [gender, setGender] = useState<'MALE' | 'FEMALE' | null>(null);
  const [ageMin, setAgeMin] = useState<string>('');
  const [ageMax, setAgeMax] = useState<string>('');
  // Not editable here — preserved so saving doesn't wipe the university
  // filter set in Afisha → "Кого ищу".
  const [universityId, setUniversityId] = useState<number | null>(null);

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
      setToast('Настройки сохранены!');
    } catch {
      setError('Не удалось сохранить настройки.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          p: 6,
          minHeight: '100%',
        }}
      >
        <CircularProgress size={32} sx={{ mb: 1.5 }} />
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main' }}>
          Загрузка настроек...
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ px: 3, pt: 2.5, pb: 4, maxWidth: 480, mx: 'auto' }}>
      <Snackbar
        open={!!toast}
        autoHideDuration={3000}
        onClose={() => setToast('')}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setToast('')} sx={{ width: '100%' }}>
          {toast}
        </Alert>
      </Snackbar>

      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 4, ml: -1 }}>
        <IconButton onClick={() => navigate('/profile')} aria-label="Назад">
          <ArrowBack />
        </IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          Настройки поиска
        </Typography>
      </Box>

      {loadFailed && (
        <Alert
          severity="error"
          sx={{ mb: 3 }}
          action={
            <Button color="inherit" size="small" onClick={loadCriteria} sx={{ textTransform: 'none' }}>
              Повторить
            </Button>
          }
        >
          Не удалось загрузить настройки поиска.
        </Alert>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Gender preference */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 1.5, fontWeight: 600 }}>
          Пол собеседника
        </Typography>
        <ToggleButtonGroup
          value={gender}
          exclusive
          onChange={(_e, val: 'MALE' | 'FEMALE' | null) => setGender(val)}
          fullWidth
          sx={{
            gap: 1.5,
            '& .MuiToggleButton-root': {
              textTransform: 'none',
              fontWeight: 600,
              fontSize: '0.95rem',
              borderRadius: '12px !important',
              py: 1.2,
              border: '1px solid',
              borderColor: 'outlineVariant.main',
            },
          }}
        >
          <ToggleButton value="MALE">Мужской</ToggleButton>
          <ToggleButton value="FEMALE">Женский</ToggleButton>
        </ToggleButtonGroup>
        <Typography
          variant="caption"
          sx={{ color: 'onSurfaceVariant.main', mt: 1, display: 'block' }}
        >
          {gender === null ? 'Любой пол (не выбрано)' : 'Нажмите ещё раз, чтобы сбросить'}
        </Typography>
      </Box>

      {/* Age range */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mb: 1.5, fontWeight: 600 }}>
          Возраст собеседника
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
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
          <Typography variant="body1" sx={{ color: 'onSurfaceVariant.main' }}>
            —
          </Typography>
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
        </Box>
      </Box>

      {/* Save button */}
      <Button
        variant="filled"
        fullWidth
        onClick={handleSave}
        disabled={saving || loadFailed}
        sx={{
          borderRadius: 3,
          py: 1.5,
          textTransform: 'none',
          fontWeight: 600,
          fontSize: '1rem',
          transition: 'transform 0.15s ease',
          '&:active': { transform: 'scale(0.97)' },
        }}
      >
        {saving ? 'Сохранение...' : 'Сохранить'}
      </Button>
    </Box>
  );
}
