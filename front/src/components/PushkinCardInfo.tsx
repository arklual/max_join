import { Box, Button, Card, Chip, Typography } from '@mui/material';
import { CreditCardOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { openMaxDeepLink } from '../api/maxBridge';
import { isMaxApp } from '../api/platform';

/**
 * Official "Госуслуги Культура" mini app in MAX — the state Pushkin card service
 * (all-Russia culture afisha with a Pushkin card filter). Verified: max.ru shows its
 * profile page; unknown names (e.g. the "pushkincard_bot" seen in search results)
 * fall back to the generic MAX landing page.
 */
const PUSHKIN_CARD_MAX_BOT = 'https://max.ru/gosuslugi_culture_bot';

function openPushkinCardInMax() {
  openMaxDeepLink(PUSHKIN_CARD_MAX_BOT);
}

/** Small chip for event cards. */
export function PushkinCardChip({ size = 'small' }: { size?: 'small' | 'medium' }) {
  return (
    <Chip
      icon={<CreditCardOutlined sx={{ fontSize: size === 'small' ? 13 : 16 }} />}
      label="По Пушкинской"
      size="small"
      sx={{
        height: size === 'small' ? 22 : 26,
        fontSize: size === 'small' ? '0.66rem' : '0.75rem',
        fontWeight: 700,
        bgcolor: 'tertiaryContainer.main',
        color: 'onTertiaryContainer.main',
        '& .MuiChip-icon': { color: 'inherit', ml: 0.75 },
      }}
    />
  );
}

interface Props {
  /** 'event' — on an event page; 'promo' — generic invitation (profile, afisha). */
  variant?: 'event' | 'promo';
}

/**
 * Explains the Pushkin card and sends the user to the official Pushkin card bot
 * in MAX (balance, card, the state afisha) — part of the MAX ecosystem.
 */
export default function PushkinCardInfo({ variant = 'event' }: Props) {
  const inMax = isMaxApp();
  return (
    <Card
      variant="outlined"
      sx={{ borderRadius: 3, p: 2, borderColor: 'tertiary.main', bgcolor: 'surfaceContainerLow.main' }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
        <Box
          sx={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'tertiaryContainer.main',
          }}
        >
          <CreditCardOutlined sx={{ color: 'onTertiaryContainer.main' }} />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '0.95rem' }}>
            {variant === 'event' ? 'Можно оплатить Пушкинской картой' : 'Пушкинская карта — 5 000 ₽ на культуру'}
          </Typography>
          <Typography variant="body2" sx={{ color: 'onSurfaceVariant.main', mt: 0.5, lineHeight: 1.5 }}>
            {variant === 'event'
              ? 'Если вам 14–22 года, билет можно купить за счёт государства: при покупке выберите оплату Пушкинской картой.'
              : 'Государство каждый год начисляет 5 000 ₽ на Пушкинскую карту всем от 14 до 22 лет — на театры, концерты и музеи. В JOIN такие события отмечены значком.'}
          </Typography>
        </Box>
      </Box>
      <Button
        variant="tonal"
        fullWidth
        onClick={openPushkinCardInMax}
        endIcon={inMax ? undefined : <OpenInNewOutlined fontSize="small" />}
        sx={{ mt: 1.5, borderRadius: 3, textTransform: 'none', fontWeight: 600 }}
      >
        {inMax ? 'Открыть «Госуслуги Культура»' : '«Госуслуги Культура» в MAX'}
      </Button>
      {!inMax && (
        <Typography variant="caption" component="div" sx={{ color: 'onSurfaceVariant.main', textAlign: 'center', mt: 0.75 }}>
          Официальный сервис Пушкинской карты работает в мессенджере MAX
        </Typography>
      )}
    </Card>
  );
}
