import type { IceBreakerResponse } from '../types';
import {
  Box,
  Paper,
  Typography,
  Button,
  Skeleton,
} from '@mui/material';
import {
  AutoAwesomeOutlined,
  ConfirmationNumberOutlined,
  CalendarTodayOutlined,
} from '@mui/icons-material';
import { openExternalLink } from '../api/maxBridge';

interface IceBreakerSectionProps {
  iceBreaker: IceBreakerResponse | null;
  loading: boolean;
  onSuggestionClick: (suggestion: string) => void;
  emptyText?: string;
}

export default function IceBreakerSection({
  iceBreaker,
  loading,
  onSuggestionClick,
  emptyText = 'Напишите первое сообщение!',
}: IceBreakerSectionProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 2,
        flex: 1,
        py: 2,
      }}
    >
      {/* Event info card */}
      {iceBreaker?.event && (
        <Paper
          elevation={0}
          sx={{
            width: '100%',
            p: 2,
            borderRadius: 3,
            bgcolor: 'primaryContainer.main',
            color: 'onPrimaryContainer.main',
            animation: 'scaleIn 0.35s ease-out',
          }}
        >
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 0.5, overflowWrap: 'anywhere' }}>
            {iceBreaker.event.title}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5 }}>
            <CalendarTodayOutlined sx={{ fontSize: 16, opacity: 0.7 }} />
            <Typography variant="body2">
              {iceBreaker.event.eventDate}
              {iceBreaker.event.eventTime ? ` · ${iceBreaker.event.eventTime}` : ''}
            </Typography>
          </Box>
          {iceBreaker.event.price && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1.5 }}>
              <ConfirmationNumberOutlined sx={{ fontSize: 16, opacity: 0.7 }} />
              <Typography variant="body2">{iceBreaker.event.price}</Typography>
            </Box>
          )}
          {iceBreaker.event.ticketUrl && (
            <Button
              variant="filled"
              size="small"
              fullWidth
              onClick={() => openExternalLink(iceBreaker.event!.ticketUrl!)}
              sx={{ borderRadius: 2, textTransform: 'none' }}
            >
              Купить билет
            </Button>
          )}
        </Paper>
      )}

      {/* Icebreaker suggestions */}
      {loading ? (
        <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
            <AutoAwesomeOutlined sx={{ fontSize: 18, color: 'primary.main' }} />
            <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 500 }}>
              Подбираем темы для разговора...
            </Typography>
          </Box>
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} variant="rounded" height={48} sx={{ borderRadius: 2 }} />
          ))}
        </Box>
      ) : iceBreaker?.suggestions && iceBreaker.suggestions.length > 0 ? (
        <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 1, animation: 'fadeInUp 0.4s ease-out' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
            <AutoAwesomeOutlined sx={{ fontSize: 18, color: 'primary.main' }} />
            <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 500 }}>
              Начните разговор:
            </Typography>
          </Box>
          {iceBreaker.suggestions.map((suggestion, index) => (
            <Paper
              key={index}
              elevation={0}
              onClick={() => onSuggestionClick(suggestion)}
              sx={{
                p: 1.5,
                borderRadius: 2,
                border: 1,
                borderColor: 'outline.main',
                bgcolor: 'surfaceContainerLow.main',
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                animation: 'fadeInUp 0.35s ease-out both',
                animationDelay: `${index * 0.08}s`,
                '&:hover': {
                  borderColor: 'primary.main',
                  bgcolor: 'surfaceContainerHigh.main',
                },
                '&:active': {
                  bgcolor: 'primaryContainer.main',
                  borderColor: 'primary.main',
                  transform: 'scale(0.98)',
                },
              }}
            >
              <Typography variant="body2" sx={{ color: 'onSurface.main', lineHeight: 1.4 }}>
                {suggestion}
              </Typography>
            </Paper>
          ))}
        </Box>
      ) : (
        <Typography variant="body2" sx={{ color: 'text.secondary', textAlign: 'center' }}>
          {emptyText}
        </Typography>
      )}
    </Box>
  );
}
