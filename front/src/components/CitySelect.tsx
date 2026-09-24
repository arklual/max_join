import { useState, useEffect, useRef, useCallback } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import CircularProgress from '@mui/material/CircularProgress';
import apiClient from '../api/client';

interface City {
  id: number;
  name: string;
}

interface CitySelectProps {
  value: string;
  onChange: (city: string) => void;
  error?: string;
  id?: string;
  className?: string;
  /** Floating label; omit when the page renders its own caption above the field. */
  label?: string;
}

const DEBOUNCE_MS = 300;

function normalize(name: string): string {
  return name.trim().toLocaleLowerCase('ru').replace(/ё/g, 'е');
}

export default function CitySelect({ value, onChange, error, id, label }: CitySelectProps) {
  const [inputValue, setInputValue] = useState(value);
  const [options, setOptions] = useState<City[]>([]);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    setInputValue(value);
  }, [value]);

  const fetchCities = useCallback(async (search: string): Promise<City[]> => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (search.trim()) {
        params.name = search.trim();
      }
      const response = await apiClient.get<City[]>('/cities', { params });
      if (requestId === requestIdRef.current) setOptions(response.data);
      return response.data;
    } catch {
      if (requestId === requestIdRef.current) setOptions([]);
      return [];
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, []);

  /**
   * If the typed text exactly (case-insensitively) matches a known city,
   * select it — so users who type the full name without tapping a
   * suggestion don't get a "Введите город" error.
   */
  async function resolveTyped(text: string) {
    const typed = normalize(text);
    if (!typed || value) return;
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
    let match = options.find((c) => normalize(c.name) === typed);
    if (!match) {
      const fetched = await fetchCities(text);
      match = fetched.find((c) => normalize(c.name) === typed);
    }
    if (match) {
      setInputValue(match.name);
      onChange(match.name);
    }
  }

  useEffect(() => {
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, []);

  const selectedCity = options.find((c) => c.name === value) ?? null;

  return (
    <Autocomplete
      id={id}
      freeSolo
      options={options}
      getOptionLabel={(option) =>
        typeof option === 'string' ? option : option.name
      }
      value={selectedCity}
      inputValue={inputValue}
      loading={loading}
      loadingText="Загрузка..."
      noOptionsText="Город не найден"
      onInputChange={(_event, newInputValue, reason) => {
        setInputValue(newInputValue);

        if (reason === 'input') {
          if (newInputValue !== value) {
            onChange('');
          }
          if (debounceRef.current) {
            clearTimeout(debounceRef.current);
          }
          debounceRef.current = setTimeout(() => {
            fetchCities(newInputValue);
          }, DEBOUNCE_MS);
        }
      }}
      onChange={(_event, newValue) => {
        if (typeof newValue === 'string') {
          // Enter pressed on free text
          void resolveTyped(newValue);
        } else if (newValue) {
          setInputValue(newValue.name);
          onChange(newValue.name);
        } else {
          setInputValue('');
          onChange('');
        }
      }}
      onOpen={() => {
        fetchCities(inputValue);
      }}
      onBlur={() => {
        void resolveTyped(inputValue);
      }}
      isOptionEqualToValue={(option, val) => option.id === val.id}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder="Начните вводить город..."
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
