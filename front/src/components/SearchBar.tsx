import type { ChangeEvent } from 'react';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import SearchOutlined from '@mui/icons-material/SearchOutlined';
import ClearOutlined from '@mui/icons-material/ClearOutlined';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export default function SearchBar({ value, onChange, placeholder = 'Поиск...' }: SearchBarProps) {
  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    onChange(e.target.value);
  }

  return (
    <TextField
      fullWidth
      variant="outlined"
      size="small"
      type="search"
      value={value}
      onChange={handleChange}
      onKeyDown={(e) => {
        // Dismiss the on-screen keyboard on "Search"/Enter
        if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
      }}
      placeholder={placeholder}
      inputProps={{ enterKeyHint: 'search', 'aria-label': placeholder }}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchOutlined sx={{ color: 'onSurfaceVariant.main' }} />
          </InputAdornment>
        ),
        endAdornment: value ? (
          <InputAdornment position="end">
            <IconButton
              size="small"
              onClick={() => onChange('')}
              aria-label="Очистить поиск"
              sx={{ color: 'onSurfaceVariant.main' }}
            >
              <ClearOutlined fontSize="small" />
            </IconButton>
          </InputAdornment>
        ) : null,
      }}
      sx={{
        '& .MuiOutlinedInput-root': {
          borderRadius: '28px',
          bgcolor: 'surfaceContainerHigh.main',
          '& fieldset': {
            borderColor: 'transparent',
          },
          '&:hover fieldset': {
            borderColor: 'outline.main',
          },
          '&.Mui-focused fieldset': {
            borderColor: 'primary.main',
          },
        },
        '& .MuiOutlinedInput-input': {
          color: 'onSurface.main',
          // Hide the native clear "x" of type=search — we render our own
          '&::-webkit-search-cancel-button, &::-webkit-search-decoration': {
            WebkitAppearance: 'none',
            appearance: 'none',
          },
        },
      }}
    />
  );
}
