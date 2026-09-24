import { useState, useEffect, useRef, useCallback } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import CircularProgress from '@mui/material/CircularProgress';
import apiClient from '../api/client';

interface University {
  id: number;
  name: string;
  city: string;
}

interface UniversitySelectProps {
  value: string;
  universityId: number | null;
  onChange: (universityName: string, universityId: number | null) => void;
  city?: string;
  error?: string;
  id?: string;
  className?: string;
  label?: string;
}

function normalize(name: string): string {
  return name.trim().toLocaleLowerCase('ru').replace(/ё/g, 'е');
}

export default function UniversitySelect({
  value,
  universityId,
  onChange,
  city,
  error,
  id,
  label,
}: UniversitySelectProps) {
  const [inputValue, setInputValue] = useState(value);
  const [options, setOptions] = useState<University[]>([]);
  const [loading, setLoading] = useState(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    setInputValue(value);
  }, [value]);

  const fetchUniversities = useCallback(async (cityParam?: string) => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (cityParam?.trim()) {
        params.city = cityParam.trim();
      }
      const response = await apiClient.get<University[]>('/universities', { params });
      if (requestId === requestIdRef.current) setOptions(response.data);
    } catch {
      if (requestId === requestIdRef.current) setOptions([]);
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, []);

  /** Select the option whose name exactly (case-insensitively) matches the typed text. */
  function resolveTyped(text: string) {
    const typed = normalize(text);
    if (!typed || universityId !== null) return;
    const match = options.find((u) => normalize(u.name) === typed);
    if (match) {
      setInputValue(match.name);
      onChange(match.name, match.id);
    }
  }

  // Re-fetch when city changes
  useEffect(() => {
    fetchUniversities(city);
  }, [city, fetchUniversities]);


  const selectedUniversity = options.find((u) => u.id === universityId) ?? null;

  return (
    <Autocomplete
      id={id}
      freeSolo
      options={options}
      getOptionLabel={(option) =>
        typeof option === 'string' ? option : option.name
      }
      value={selectedUniversity}
      inputValue={inputValue}
      loading={loading}
      loadingText="Загрузка..."
      noOptionsText={inputValue.trim() ? 'ВУЗ не найден' : 'Нет данных'}
      filterOptions={(opts, state) => {
        const trimmed = state.inputValue.trim().toLowerCase();
        if (!trimmed) return opts;
        return opts.filter((u) => u.name.toLowerCase().includes(trimmed));
      }}
      onInputChange={(_event, newInputValue, reason) => {
        setInputValue(newInputValue);

        if (reason === 'input') {
          if (newInputValue !== value) {
            onChange('', null);
          }
        }
      }}
      onChange={(_event, newValue) => {
        if (typeof newValue === 'string') {
          // Enter pressed on free text
          resolveTyped(newValue);
        } else if (newValue) {
          setInputValue(newValue.name);
          onChange(newValue.name, newValue.id);
        } else {
          setInputValue('');
          onChange('', null);
        }
      }}
      onOpen={() => {
        fetchUniversities(city);
      }}
      onBlur={() => {
        resolveTyped(inputValue);
      }}
      isOptionEqualToValue={(option, val) => option.id === val.id}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder="Начните вводить название ВУЗа..."
          error={!!error}
          helperText={error}
          variant="outlined"
          size="small"
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading ? <CircularProgress color="inherit" size={20} /> : null}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
          sx={{
            '& .MuiOutlinedInput-root': {
              borderRadius: '12px',
              bgcolor: 'surfaceContainerHigh.main',
              '& fieldset': {
                borderColor: error ? 'error.main' : 'outline.main',
              },
              '&:hover fieldset': {
                borderColor: error ? 'error.main' : 'primary.main',
              },
              '&.Mui-focused fieldset': {
                borderColor: error ? 'error.main' : 'primary.main',
              },
            },
            '& .MuiOutlinedInput-input': {
              color: 'onSurface.main',
            },
          }}
        />
      )}
      sx={{
        width: '100%',
        '& .MuiAutocomplete-paper': {
          bgcolor: 'surfaceContainer.main',
          borderRadius: '12px',
        },
      }}
    />
  );
}
